package nju.merge.core;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
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
                Git git = Git.cloneRepository()
                        .setURI(url)
                        .setDirectory(new File(path))
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


    public static List<RevCommit> getMergeCommits(Repository repository) throws Exception {
        // todo git log --merges --min-parents=2 --max-parents=2
        
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
