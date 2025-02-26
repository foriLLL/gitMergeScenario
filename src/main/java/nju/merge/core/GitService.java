package nju.merge.core;

import nju.merge.IO.PathUtil;
import nju.merge.entity.CommitMergeScenario;
import nju.merge.entity.MergeScenario;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.RecursiveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitService {

    private static final Logger logger = LoggerFactory.getLogger(GitService.class);
    private String projectName;
    private String projectPath;
    private String conflictOutput;

    private Repository repo;

    public GitService(){}
    public GitService(String projectName, String projectPath, String conflictOutput){
        this.conflictOutput = conflictOutput;
        this.projectPath = projectPath;
        this.projectName = projectName;
    }


    private Repository CloneIfNotExist(String path, String url) throws Exception {
        File gitFolder = new File(path);
        Repository repo;
        if(gitFolder.exists()) {
            logger.info("git repo {} is found...........", path);
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repo = builder.setGitDir(new File(gitFolder, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
        } else{
            logger.info("downloading git repo from {}...........", url);
            Git git = Git.cloneRepository()
                    .setGitDir(gitFolder)
                    .setURI(url)
                    .setCloneAllBranches(true)
                    .call();
            repo = git.getRepository();
        }
        return repo;
    }

    public void collectAllConflicts(String projectPath, String projectName, String url, String output) throws Exception{
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.conflictOutput = output;
        this.repo = CloneIfNotExist(this.projectPath,url);
        List<RevCommit> commits = collectMergeCommits();
        List<CommitMergeScenario> commitMergeScenarios = new ArrayList<>();
        for(RevCommit c : commits){
            mergeAndGetCMS(c, commitMergeScenarios);
        }
        if(!commitMergeScenarios.isEmpty()) {
            for (var cms : commitMergeScenarios) {
                collectAllConflictFiles(cms);
            }
        }
        threeWayMergeFile(conflictOutput);
    }

    private List<RevCommit> collectMergeCommits() throws Exception {
        logger.info("collecting merge commits");
        List<RevCommit> commits = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(repo)) {
            for (Ref ref : repo.getRefDatabase().getRefs()) {
                revWalk.markStart(revWalk.parseCommit(ref.getObjectId()));
            }
            for (RevCommit commit : revWalk) {
                if(commit.getParentCount() == 2){
                    commits.add(commit);
                }
            }
        }
        return commits;
    }

    private void mergeAndGetCMS(RevCommit merged, List<CommitMergeScenario> mergeScenarios) throws Exception {
        RevCommit p1 = merged.getParents()[0];
        RevCommit p2 = merged.getParents()[1];
        logger.info("merge {} and {}, child commit {}", p1.getName(), p2.getName(), merged.getName());
        ThreeWayMerger merger = MergeStrategy.RECURSIVE.newMerger(repo, true);
        if(!merger.merge(p1, p2)){
            RecursiveMerger rMerger = (RecursiveMerger)merger;
            RevCommit base = (RevCommit) rMerger.getBaseCommitId();
            CommitMergeScenario cms = new CommitMergeScenario();
            rMerger.getMergeResults().forEach((file, result) -> {
                if(file.endsWith(".java") && result.containsConflicts()){
                    logger.info("conflicts were found in {}", file);
                    cms.conflictFiles.add(file);
                }
            });
            if(cms.conflictFiles.size() != 0){
                cms.base = base;
                cms.ours = p1;
                cms.theirs = p2;
                cms.truth = merged;
                cms.commitId = merged.getName();
                mergeScenarios.add(cms);
            }
        }
    }


    private void collectAllConflictFiles(CommitMergeScenario cms) throws Exception {
        Map<String, MergeScenario> scenarioMap = new HashMap<>();
        for(var file : cms.conflictFiles){
            scenarioMap.put(file, new MergeScenario(projectName, cms.commitId, file));
        }
        if(scenarioMap.size() == 0) return;
        RevCommit merged = cms.truth;
        RevCommit base = cms.base;
        RevCommit p1 = cms.ours;
        RevCommit p2 = cms.theirs;
        logger.info("collecting scenario in merged commit {}", merged.getName());
        scenarioMap.forEach((file, scenario) -> {
            try {
                scenario.truth = getFileWithCommitAndPath(file, merged);
                scenario.ours = getFileWithCommitAndPath(file, p1);
                scenario.theirs = getFileWithCommitAndPath(file, p2);
                if(isBaseExist(base)){
                    scenario.base = getFileWithCommitAndPath(file, base);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        scenarioMap.forEach((f, s)-> {
            try {
                s.write2folder(conflictOutput);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private boolean isBaseExist(ObjectId id) throws IOException {
        RevWalk walk = new RevWalk(this.repo);
        try {
            AnyObjectId a = walk.parseAny(id);
        }catch (MissingObjectException e){
            logger.info("can't find base {}", id.getName());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private byte[] getFileWithCommitAndPath(String filePath, RevCommit commit) throws IOException {
        TreeWalk treeWalk = TreeWalk.forPath(this.repo, filePath, commit.getTree());
        if(treeWalk == null) return null;
        ObjectLoader objectLoader = this.repo.open(treeWalk.getObjectId(0));
        return objectLoader.getBytes();
    }

    private byte[] getFileBytes(String path) throws IOException {
        path = PathUtil.getSystemCompatiblePath(path);

        File file = new File(path);
        if(file.exists()) {
            Path curPath = Paths.get(path);
            return Files.readAllBytes(curPath);
        }else{
            return null;
        }
    }


    private void threeWayMergeFile(String dir) throws IOException {
        Path path = Paths.get(dir);
        Files.walkFileTree(path, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.toString().endsWith(".java")) {
                    File[] fs = dir.toFile().listFiles();
                    File base = null, a = null, b = null, truth = null;
                    for (var f : fs) {
                        if (f.getName().equals("base.java")) base = f;
                        else if (f.getName().equals("ours.java")) a = f;
                        else if (f.getName().equals("theirs.java")) b = f;
                        else if (f.getName().equals("truth.java")) truth = f;
                    }
                    if (base != null && a != null && b != null && truth != null) {
                        {
                            File conflict = new File(dir.toString(), "conflict.java");
                            if(conflict.exists()) conflict.delete();
                            Files.copy(a.toPath(), conflict.toPath());
                            logger.info("git merge-file --diff3 {} {} {}", conflict.getPath(), base.getPath(), b.getPath());
                            ProcessBuilder pb2 = new ProcessBuilder(
                                    "git",
                                    "merge-file",
                                    "--diff3",
                                    conflict.getPath(),
                                    base.getPath(),
                                    b.getPath());
                            try {
                                pb2.start().waitFor();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
