package nju.merge.client;

import nju.merge.core.ChunkCollector;
import nju.merge.core.ConflictCollector;
import nju.merge.core.ChunkFilter;
import nju.merge.utils.JSONUtils;
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

public class Client {


    private static String workdir = "";
    private static String reposDir = workdir + "/repos";   // store all the repos
    private static String outputDir = workdir + "/output";
    private static String repoList = workdir + "/list.txt";
    private static Logger logger = LoggerFactory.getLogger(Client.class);
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
    
    public static void main(String[] args) throws Exception{
        Options options = new Options();
        options.addOption("d", "workDir", true, "work directory");
        options.addOption("p", "projectPath", true, "projectPath");
        options.addOption("s", "status", true, "status");
        CommandLineParser parser = new DefaultParser();
        String projectPath = "";
        String s = "1";
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("p")) {
            projectPath = cmd.getOptionValue("p");
        }
        if(cmd.hasOption("d")){
            workdir = cmd.getOptionValue("d");
        }
        if(cmd.hasOption("s")){
            s = cmd.getOptionValue("s");
        }
        reposDir = workdir + "/repos";
        outputDir = workdir + "/output";
        repoList = workdir + "/list.txt";

        Map<String, String> repos = new HashMap<>();
        if(projectPath.equals("")) {
            addReposFromText(repoList, repos);
        }
        else{
            repos.put(projectPath, "");
        }
        String finalS = s;
        repos.forEach((projectName, url) -> {
            String repoPath = PathUtils.getFileWithPathSegment(reposDir, projectName);                      // store the specific repo
            String outputConflictPath = PathUtils.getFileWithPathSegment(outputDir, "conflictFiles");       // store all conflict files during collecting
            String outputJsonPath = PathUtils.getFileWithPathSegment(outputDir, "mergeTuples");             // store output tuples
            String filteredTuplePath = PathUtils.getFileWithPathSegment(outputDir, "filteredTuples");       // store filtered tuples
            try {
                if(finalS.contains("1")){
                    logger.info("-------------------------- Collect conflict files ----------------------------------");
                    collectMergeConflict(repoPath, projectName, url, outputConflictPath, allowedExtensions);
                }
                if(finalS.contains("2")){
                    logger.info("-------------------------- Collect merge tuples ----------------------------------");
                    collectMergeTuples(outputJsonPath, projectName, outputConflictPath);
                }
                if(finalS.contains("3")){
                    logger.info("-------------------------- Merge tuples analysis ----------------------------------");
                    mergeTuplesAnalysis(PathUtils.getFileWithPathSegment(outputJsonPath, projectName + ".json"), projectName, filteredTuplePath);
                }
//                deleteRepo(repoPath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void deleteRepo(String repoPath) throws IOException {
        FileUtils.deleteDirectory(new File(repoPath));
    }

    public static void collectMergeConflict(String projectPath, String projectName, String url, String output, Set<String> allowedExtensions) throws Exception {
        ConflictCollector collector = new ConflictCollector(projectPath, projectName, url, output, allowedExtensions);
        collector.process();
    }

    public static void collectMergeTuples(String outputFile, String projectName, String conflictFilesPath) throws Exception {
        ChunkCollector collector = new ChunkCollector();
        collector.extractFromProject(PathUtils.getFileWithPathSegment(conflictFilesPath, projectName));
        JSONUtils.writeTuples2Json(collector.mergeTuples, projectName, outputFile);
    }

    public static void mergeTuplesAnalysis(String jsonPath, String projectName, String outputDir) throws Exception {
        ChunkFilter filter = new ChunkFilter(jsonPath, projectName, outputDir);
        filter.analysis();
//        filter.analysisDefault();
    }
}
