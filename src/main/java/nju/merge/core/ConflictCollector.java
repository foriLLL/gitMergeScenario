package nju.merge.core;

import nju.merge.entity.ConflictChunk;
import nju.merge.entity.ConflictFile;
import nju.merge.entity.MergeConflict;
import nju.merge.entity.MergeScenario;
import nju.merge.utils.PathUtils;

import org.checkerframework.checker.units.qual.s;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.RecursiveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.IntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class ConflictCollector {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);
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
    }

    /**
     * Get base, ours, theirs, truth and conflict versions of all java source files with conflicts.
     * Conflict files contain conflict blocks.
     */
    public void process() throws Exception {
        GitService service = new GitService();
        repository = service.cloneIfNotExist(this.projectPath, URL);

        List<RevCommit> mergeCommits = service.getMergeCommits(repository);     // 所有有 2 个 parent 的提交
        List<MergeConflict> conflictList = new ArrayList<>();                   // 所有有冲突的提交，每个记录所有有冲突的文件（不是冲突块），用于创建文件目录

        for (RevCommit commit : mergeCommits) {
            mergeAndGetConflict(commit, conflictList);
        }

        // 写入文件


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
    private void mergeAndGetConflict(RevCommit resolve, List<MergeConflict> conflictList) throws Exception {
        RevCommit ours = resolve.getParents()[0];
        RevCommit theirs = resolve.getParents()[1];

        ThreeWayMerger merger = MergeStrategy.RECURSIVE.newMerger(repository, true);
        if (!merger.merge(ours, theirs)) {                          // conflicts found
            RecursiveMerger rMerger = (RecursiveMerger) merger;
            RevCommit base = (RevCommit) rMerger.getBaseCommitId();

            MergeConflict conflict = new MergeConflict();
            rMerger.getMergeResults().forEach((file, result) -> {           // result 有 chunk 属性，包含合并后文件的所有内容来源
                if (isTargetFileType(file) && result.containsConflicts()) {
                    logger.info("file {} added", file);
                    // conflict.conflictFiles.add(file);

                    // todo 在这里记录三个文件内容，同时记录所有冲突块以及上下文
                    System.out.println("file: " + file);                        // todo file 是相对路径
                    String[] baseContent;
                    String[] oursContent;
                    String[] theirsContent;
                    ArrayList<String> mergedContent = new ArrayList<>();
                    String[] resolvedContent;
                    ArrayList<ConflictChunk> conflictChunks = new ArrayList<>();
                    try {
                        // 获取各文件内容
                        baseContent = new String(((RawText)result.getSequences().get(0)).getRawContent()).split("\n");
                        oursContent = new String(((RawText)result.getSequences().get(1)).getRawContent()).split("\n");
                        theirsContent = new String(((RawText)result.getSequences().get(2)).getRawContent()).split("\n");
                        // todo 获取到各个内容，根据 chunk 的内容，将冲突块标记出来，生成 conflictChunk，同时生成 mergedContent
                        IntList mergeResultChunkTuples = result.chunks;
                        for (int i = 0; i < mergeResultChunkTuples.size();) {
                            int state = mergeResultChunkTuples.get(i);
                            if (state == 0) {
                                // 0 means no conflict
                                mergedContent.addAll(Arrays.asList(baseContent).subList(mergeResultChunkTuples.get(i+2), mergeResultChunkTuples.get(i+3)));
                                i += 4;
                                continue;
                            }
                            // construct a conflict chunk
                            ConflictChunk chunk = new ConflictChunk();
                            chunk.repositoryName = projectName;
                            chunk.filePath = file;
                            chunk.mergedCommitHash = resolve.getName();
                            chunk.startLine = mergedContent.size();
                            
                            int startLine, endLine;
                            // ours
                            mergedContent.add("<<<<<<< ours");
                            startLine = mergeResultChunkTuples.get(i+2);
                            endLine = mergeResultChunkTuples.get(i+3);
                            chunk.ours = Arrays.copyOfRange(oursContent, startLine, endLine);
                            mergedContent.addAll(Arrays.asList(chunk.ours));
                            i+=4;
                            // base
                            mergedContent.add("||||||| base");
                            startLine = mergeResultChunkTuples.get(i+2);
                            endLine = mergeResultChunkTuples.get(i+3);
                            chunk.base = Arrays.copyOfRange(baseContent, startLine, endLine);
                            mergedContent.addAll(Arrays.asList(chunk.base));
                            i+=4;
                            // theirs
                            mergedContent.add("=======");
                            startLine = mergeResultChunkTuples.get(i+2);
                            endLine = mergeResultChunkTuples.get(i+3);
                            chunk.theirs = Arrays.copyOfRange(theirsContent, startLine, endLine);
                            mergedContent.addAll(Arrays.asList(chunk.theirs));
                            i+=4;
                            mergedContent.add(">>>>>>> theirs");
                            chunk.endLine = mergedContent.size();
                            // done

                            conflictChunks.add(chunk);
                            System.out.println(chunk);
                        }
                        // resolvedContent 提取自 resove commit
                        resolvedContent = GitService.getFileContent(this.repository, resolve, file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // new ConflictFile(base., file, file, file, file, file, file, file, file, file)
                }
            });
            return;
        }
    }
}
