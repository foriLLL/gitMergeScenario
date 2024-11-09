package nju.merge.client;

import nju.merge.core.ConflictCollector;
import nju.merge.utils.PathUtils;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;

public class Client {


//    private static String workdir = "";
//    private static String reposDir = workdir + "/repos";   // store all the repos
//    private static String outputDir = workdir + "/output";
//    private static String repoList = workdir + "/list.txt";
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    public static final Set<String> allowedExtensions = new HashSet<>(Arrays.asList(
            "py",    // Python
            "js",    // JavaScript
            "ts",    // TypeScript
            "go",    // Go
            "java",  // Java
            "cpp",   // C++
            "c",     // C
            "h",     // C Header
            "hpp",   // C++ Header
            "rb",    // Ruby
            "php",   // PHP
            "cs",    // C#
            "swift", // Swift
            "rs",    // Rust
            "m",     // Objective-C
            "mm"     // Objective-C++
    ));

    public static void addReposFromText(String txtPath, Map<String, String> repos) throws IOException {
        Path path = Paths.get(txtPath);
        List<String> lines = Files.readAllLines(path);
        lines.forEach(line -> {
            String[] args = line.split(",");
            repos.put(args[0].strip(), args[1].strip());
        });
    }
    
//    public static void main(String[] args) throws Exception{
//
//        System.setProperty("http.proxyHost", "114.212.86.64");
//        System.setProperty("http.proxyPort", "7890");
//        System.setProperty("https.proxyHost", "114.212.86.64");
//        System.setProperty("https.proxyPort", "7890");
//
//        Options options = new Options();
//        options.addOption("d", "workDir", true, "work directory");
//        options.addOption("p", "projectPath", true, "projectPath");
//        options.addOption("s", "status", true, "status");
//        CommandLineParser parser = new DefaultParser();
//        String projectPath = "";
//        String s = "1";
//        CommandLine cmd = parser.parse(options, args);
//
//        if (cmd.hasOption("p")) {
//            projectPath = cmd.getOptionValue("p");
//        }
//        if(cmd.hasOption("d")){
//            workdir = cmd.getOptionValue("d");
//        }
//        if(cmd.hasOption("s")){
//            s = cmd.getOptionValue("s");
//        }
//        reposDir = workdir + "/repos";
//        outputDir = workdir + "/output";
//        repoList = workdir + "/list.txt";
//
//        Map<String, String> repos = new HashMap<>();
//        if(projectPath.equals("")) {
//            addReposFromText(repoList, repos);
//        }
//        else{
//            repos.put(projectPath, "");
//        }
//        String finalS = s;
//
//        // 创建固定大小的线程池
//        int threadPoolSize = 10; // 你想要的线程数量
//        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
//
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//        AtomicInteger completedCnt = new AtomicInteger(0);
//        repos.forEach((projectName, url) -> {
//            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                String repoPath = PathUtils.getFileWithPathSegment(reposDir, projectName);
//                String outputConflictPath = PathUtils.getFileWithPathSegment(outputDir, "conflictFiles");
//                try {
//                    if (finalS.contains("1")) {
//                        logger.error("Processing repo: {} on thread: {}", projectName, Thread.currentThread().getName());
//                        collectMergeConflict(repoPath, projectName, url, outputConflictPath, allowedExtensions);
////                        deleteRepo(repoPath);       // 硬盘不够大，收集完就删除
//                    }
//                } catch (Exception e) {
//                    logger.error("Error processing repo: " + projectName, e);
//                }
//                int completed = completedCnt.incrementAndGet();
//                logger.info("Completed: {}/{}, {}%", completed, repos.size(), completed * 100.0 / repos.size());
//            }, executorService);
//            futures.add(future);
//        });
//
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//    }

    public static void deleteRepo(String path2del) {
        try {
            FileUtils.deleteDirectory(new File(path2del));
        } catch (IOException e) {
            logger.error("path-to-delete is not a directory: {}", path2del, e);
        }
    }

    public static void collectMergeConflict(String repoPath, String projectName, String url, String output, Set<String> allowedExtensions) {
        try {
            ConflictCollector collector = new ConflictCollector(repoPath, projectName, url, output, allowedExtensions);
            collector.process();
        } catch (Exception e) {
            logger.error("收集遇到问题：{}", repoPath, e);
        }
        deleteRepo(repoPath);       // 硬盘不够大，收集完就删除
    }
}
