package nju.merge.core;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeAlgorithm;
import org.eclipse.jgit.merge.MergeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GitService {

    private static final Logger logger = LoggerFactory.getLogger(GitService.class);

    public GitService(){}

    public static Repository cloneIfNotExist(String path, String url) throws Exception {
        File gitFolder = new File(path);
        Repository repo;
        if(gitFolder.exists()) {
            logger.info("仓库已存在本地：{}", path);
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repo = builder.setGitDir(new File(gitFolder, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            return repo;
        } else{
            try {
                logger.info("开始克隆仓库 {}...... ", url);
                // https://github.com/eclipse-jgit/jgit/tree/master/org.eclipse.jgit.ssh.jsch
                // 需要 export GIT_SSH=/usr/bin/ssh
                Git git = Git.cloneRepository()
                        .setURI(url)
                        .setDirectory(new File(path))
                        .setTimeout(600)
                        .call();

                logger.info("克隆完成");
                return git.getRepository();
            } catch (GitAPIException e) {
                logger.warn("克隆仓库失败 {}", url);
                // e.printStackTrace();
                throw e;
            }
        }
    }

    public static String[] getMergedContent(String[] oursContent, String[] theirsContent, String[] baseContent) throws IOException {
        // 这里的 String[] 需要使用 split("\n", -1)，以使用 join 正确处理末尾的换行符

        RawText base = new RawText((String.join("\n", baseContent) + "\n").getBytes(StandardCharsets.UTF_8));
        RawText ours = new RawText((String.join("\n", oursContent) + "\n").getBytes(StandardCharsets.UTF_8));
        RawText theirs = new RawText((String.join("\n", theirsContent) + "\n").getBytes(StandardCharsets.UTF_8));

        MergeAlgorithm mergeAlgorithm = new MergeAlgorithm();
        org.eclipse.jgit.merge.MergeResult<RawText> mergeResult = mergeAlgorithm.merge(
                RawTextComparator.DEFAULT, base, ours, theirs);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MergeFormatter formatter = new MergeFormatter();

        // 使用 formatMergeDiff3 方法输出 diff3 风格
        formatter.formatMergeDiff3(
                out,
                mergeResult,
                Arrays.asList("BASE", "OURS", "THEIRS"),
                StandardCharsets.UTF_8
        );

        return out.toString(StandardCharsets.UTF_8).split("\n", -1);
    }

    public static List<RevCommit> getMergeCommits(Repository repository) throws Exception {
        // todo git log --merges --min-parents=2 --max-parents=2 不知道性能会好吗？感觉本质上也是遍历
        
        logger.info("Collecting merge commits");

        List<RevCommit> commits = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(repository)) {
            for (Ref ref : repository.getRefDatabase().getRefs()) {
                revWalk.markStart(revWalk.parseCommit(ref.getObjectId()));
            }
            for (RevCommit commit : revWalk) {
                if (commit.getParentCount() == 2) {
                    commits.add(commit);
                }
            }
        } catch (IOException e) {
            logger.error("Error while collecting merge commits", e);
            logger.error("repository: {}", repository.getDirectory().getAbsolutePath());
            logger.error("------------------------------------");
        }
        return commits;
    }

    public static String[] getFileContent(Repository repository, RevCommit commit, String filePath) throws IOException {
        // 创建一个 TreeWalk 对象，用于遍历 commit 中的树
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            // 指定需要解析的 commit 树
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true); // 设置为递归模式，以便查找文件
            treeWalk.setFilter(PathFilter.create(filePath));

            // 找到指定文件并读取内容
            if (!treeWalk.next()) {
                throw new IOException("File not found: " + filePath);
            }

            // 获取文件内容
            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repository.open(objectId);
            byte[] fileContent = loader.getBytes();
            return new String(fileContent, StandardCharsets.UTF_8).split("\n");
        }
    }

}
