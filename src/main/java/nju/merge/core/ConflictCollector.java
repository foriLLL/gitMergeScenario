package nju.merge.core;

import nju.merge.entity.ConflictFile;
import nju.merge.entity.MergeConflict;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.merge.MergeChunk;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.RecursiveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.merge.MergeChunk.ConflictState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ConflictCollector {
    private static final Logger logger = LoggerFactory.getLogger(ConflictCollector.class);
    private final String projectName;
    private final String projectPath;
    private final String URL;
    private final String output;
    private Repository repository;

    private final Set<String> allowedExtentions;

    public ConflictCollector(String projectPath, String projectName, String url, String output, Set<String> allowedExtentions) {
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.URL = url;
        this.output = output;
        this.allowedExtentions = allowedExtentions;
        try {
            repository = GitService.cloneIfNotExist(this.projectPath, URL);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("仓库克隆失败：" + URL, e);
            return;
        }
    }
    
    /**
     * Get base, ours, theirs, truth and conflict versions of all java source files with conflicts.
     * Conflict files contain conflict blocks.
     */
    public void process() throws Exception {
        if (repository == null) {
            return;
        }
        List<RevCommit> mergeCommits = GitService.getMergeCommits(repository);     // 所有有 2 个 parent 的提交
        List<MergeConflict> conflictList = new ArrayList<>();                   // 所有有冲突的提交，每个记录所有有冲突的文件（不是冲突块），用于创建文件目录

        for (RevCommit commit : mergeCommits) {
            mergeAndGetConflict(commit, conflictList);
        }
    }

    private Boolean isTargetFileType(String filename){
        // 将文件名按 "." 分割成数组
        String[] parts = filename.split("\\.");

        // 确保文件名包含扩展名，并且扩展名在 allowedExtensions 中
        if (parts.length > 1) {
            String extension = parts[parts.length - 1];  // 获取最后一个部分作为扩展名
            return this.allowedExtentions.contains(extension);
        }

        // 没有扩展名的文件
        return false;
    }

    private void writeConflictFiles(String outputPath, List<ConflictFile> conflictFiles, String resolveHash) {
        for (ConflictFile conflictFile : conflictFiles) {
            String relativePath = conflictFile.filePath;
            
            try {
                // 创建基本目录路径
                Path basePath = Paths.get(outputPath, projectName, resolveHash, relativePath);
    
                // 创建各个版本的目录和文件
                writeContent(basePath.resolve("base"), conflictFile.baseContent);
                writeContent(basePath.resolve("ours"), conflictFile.oursContent);
                writeContent(basePath.resolve("theirs"), conflictFile.theirsContent);
                writeContent(basePath.resolve("thuth"), conflictFile.resolvedContent);
                writeContent(basePath.resolve("merged"), conflictFile.mergedContent);
            } catch (IOException e) {
                e.printStackTrace();
                logger.error("Failed to write conflict file: " + relativePath, e);
            }
        }
    }
    
    private void writeContent(Path filePath, String[] content) throws IOException {
        // 确保目录存在
        Files.createDirectories(filePath.getParent());
        
        // 写入文件内容
        Files.write(filePath, Arrays.asList(content), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void mergeAndGetConflict(RevCommit resolve, List<MergeConflict> conflictList) {
        RevCommit ours = resolve.getParents()[0];
        RevCommit theirs = resolve.getParents()[1];

        ThreeWayMerger merger = MergeStrategy.RECURSIVE.newMerger(repository, true);
        try {
            if (!merger.merge(ours, theirs)) {                          // conflicts found
                RecursiveMerger rMerger = (RecursiveMerger) merger;
                RevCommit base = (RevCommit) rMerger.getBaseCommitId();
                List<ConflictFile> conflictFiles = new ArrayList<>();
                rMerger.getMergeResults().forEach((file, result) -> {           // result 有 chunk 属性，包含合并后文件的所有内容来源
                    if (isTargetFileType(file) && result.containsConflicts()) {
                        logger.info("file {} added", file);
                        
                        // 在这里记录文件内容，同时记录所有冲突块以及上下文
                        try {
                            // resolvedContent 提取自 resove commit
                            String[] resolvedContent = GitService.getFileContent(this.repository, resolve, file);
                            // 获取各文件内容
                            String[][] contents = new String[3][];
                            for (int i = 0; i < 3; i++) {
                                contents[i] = new String(((RawText)result.getSequences().get(i)).getRawContent()).split("\n", -1);
                            }
                            ArrayList<String> mergedContent = new ArrayList<>();
                            if (base == null) {
                                logger.error("repo:" + projectName);
                                logger.error("resolve:" + resolve);
                                logger.error("file:" + file);
                                return;
                            }
                            ConflictFile conflictFile = new ConflictFile(
                                contents[0], contents[1], contents[2], 
                                null, resolvedContent, resolve.getName(), base.getName(), ours.getName(), theirs.getName(), file, projectName
                            );

                            // 获取到各个内容，根据 chunk 的内容，将冲突块标记出来，生成 conflictChunk，同时生成 mergedContent
                            String[][] chunkContents = new String[3][];
                            int startLine = -1, endLine = -1;
                            for (MergeChunk chunk : result) {
                                int srcIdx = chunk.getSequenceIndex();
                                int begin = chunk.getBegin();
                                int end = chunk.getEnd();
                                if (begin > end) {
                                    // ! 因为是 diff 的结果，所以可能会出现这种情况，说明是删除操作
                                    begin = end;
                                }
                                ConflictState state = chunk.getConflictState();
                                
                                if (end > contents[srcIdx].length || end < 0 || begin < 0) {
                                    // writeContent(Paths.get("debug", "base"), contents[0]);
                                    // writeContent(Paths.get("debug", "ours"), contents[1]);
                                    // writeContent(Paths.get("debug", "theirs"), contents[2]);
                                    // writeContent(Paths.get("debug", "resolved"), resolvedContent);
                                    begin = end = 0;
                                }
                                if (state == ConflictState.NO_CONFLICT) {
                                    mergedContent.addAll(Arrays.asList(contents[srcIdx]).subList(begin, end));
                                    continue;
                                }
                                // 冲突块
                                
                                chunkContents[srcIdx] = Arrays.copyOfRange(contents[srcIdx], begin, end);
                                if (state == ConflictState.FIRST_CONFLICTING_RANGE) {
                                    startLine = mergedContent.size();
                                    mergedContent.add("<<<<<<< ours");
                                    mergedContent.addAll(Arrays.asList(chunkContents[srcIdx]));
                                } else if (state == ConflictState.BASE_CONFLICTING_RANGE) {
                                    mergedContent.add("||||||| base");
                                    mergedContent.addAll(Arrays.asList(chunkContents[srcIdx]));
                                } else if (state == ConflictState.NEXT_CONFLICTING_RANGE) {
                                    mergedContent.add("=======");
                                    mergedContent.addAll(Arrays.asList(chunkContents[srcIdx]));
                                    mergedContent.add(">>>>>>> theirs");
                                    endLine = mergedContent.size();
                                    conflictFile.addConflictChunk(chunkContents[0], chunkContents[1], chunkContents[2], startLine, endLine);
                                }
                            }
                            conflictFile.mergedContent = mergedContent.toArray(new String[0]);
                            conflictFiles.add(conflictFile);
                        } catch (IOException e) {
                            // most likely file not found
                            logger.warn(e.getMessage());
                        }
                    }
                });
                if (!conflictFiles.isEmpty()) {
                    // 写入文件
                    // output 为输出目录，在 output 下新建 projectName 文件夹，其下根据 resolved commit 的 hash 值新建文件夹
                    // 每个文件夹下包含本次合并的所有冲突文件，包括 base, ours, theirs, truth, merged
                    // 如： 
                    // output/projectName/resolveHash/nju/merge/core/ConflictCollector.java/base
                    // output/projectName/resolveHash/nju/merge/core/ConflictCollector.java/ours
                    // output/projectName/resolveHash/nju/merge/core/ConflictCollector.java/theirs
                    // output/projectName/resolveHash/nju/merge/core/ConflictCollector.java/thuth
                    // output/projectName/resolveHash/nju/merge/core/ConflictCollector.java/merged
                    writeConflictFiles(output, conflictFiles, resolve.getName());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
