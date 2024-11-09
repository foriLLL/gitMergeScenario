package nju.merge.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import nju.merge.entity.ConflictFile;
import nju.merge.entity.MergeConflict;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.RecursiveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
            logger.error("仓库克隆失败：{}", URL, e);
            try {
                // 将 project name, url 追加写入 error_clone.txt
                // 确保 paths 存在
                Path errorPath = Paths.get(output, "error_clone.txt");
                Files.createDirectories(errorPath.getParent());
                Files.write(errorPath, Collections.singletonList(projectName + "," + URL), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ioException) {
                logger.error("Failed to write error_clone.txt", ioException);
            }
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

        for (int i = 0; i < mergeCommits.size(); i++) {
            RevCommit commit = mergeCommits.get(i);
            if (i % 200 == 0) logger.info("commit progress: {} out of {} merge commits, {}%", i, mergeCommits.size(), i * 100.0 / mergeCommits.size());
            mergeAndGetConflict(commit, conflictList);
        }
    }

    private boolean isTargetFileType(String filename){
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
        Path jsonPath = Paths.get(outputPath, projectName, resolveHash, "conflictFilesMetadata.json");
        String jsonString = JSON.toJSONString(conflictFiles, SerializerFeature.PrettyFormat);
        try {
            writeContent(jsonPath, new String[]{jsonString});
        } catch (IOException e) {
            logger.error("Failed to write conflict files", e);
        }

        for (ConflictFile conflictFile : conflictFiles) {
            String relativePath = conflictFile.filePath;
            try {
                // 创建基本目录路径
                Path basePath = Paths.get(outputPath, projectName, resolveHash, relativePath.replace("/", ":"));

                // 创建各个版本的目录和文件
                writeContent(basePath.resolve("base"), conflictFile.baseContent);
                writeContent(basePath.resolve("ours"), conflictFile.oursContent);
                writeContent(basePath.resolve("theirs"), conflictFile.theirsContent);
                writeContent(basePath.resolve("truth"), conflictFile.resolvedContent);
//                writeContent(basePath.resolve("merged_generated_through_chunk"), conflictFile.mergedContent);

            } catch (IOException e) {
                logger.error("Failed to write conflict file: {}", relativePath, e);
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
                if (base == null) {
                    // 不收集 base 为 null 的情况
                    logger.error("base is null, {}, {}", projectName, resolve);
                    return;
                }
                List<ConflictFile> conflictFiles = new ArrayList<>();
                AtomicInteger processedFileCount = new AtomicInteger();
                rMerger.getMergeResults().forEach((file, result) -> {           // result 有 chunk 属性，包含合并后文件的所有内容来源
                    if (processedFileCount.get() % 100 == 0) logger.info("file progress: {} out of {} merge commits, {}%", processedFileCount.incrementAndGet(), rMerger.getMergeResults().size(), processedFileCount.get() * 100.0 / rMerger.getMergeResults().size());
                    if (isTargetFileType(file) && result.containsConflicts()) {
                        // 在这里记录文件内容，同时记录所有冲突块以及上下文
                        try {
                            // resolvedContent 提取自 resolve commit
                            // todo：是不是可以 track 到位置移动后的文件？
                            String[] resolvedContent = GitService.getFileContent(this.repository, resolve, file);
                            // 获取各文件内容
                            String[][] contents = new String[3][];
                            for (int i = 0; i < 3; i++) {
                                contents[i] = new String(((RawText)result.getSequences().get(i)).getRawContent()).split("\n", -1);
                            }
                            ConflictFile conflictFile = new ConflictFile(
                                contents[0], contents[1], contents[2], 
                                null, resolvedContent, resolve.getName(), base.getName(), ours.getName(), theirs.getName(), file, projectName
                            );
                            conflictFiles.add(conflictFile);
                        } catch (IOException e) {
                            // most likely file not found
                            logger.warn("file with no corresponding resolved file: {}, {}, {}", projectName, resolve, file);
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
