package nju.merge.client;

import java.util.HashMap;
import java.util.Set;

import org.junit.jupiter.api.Test;
// import static org.junit.jupiter.api.Assertions.assertEquals;

import nju.merge.utils.PathUtils;

public class ClientTest {
    private static String workdir = "";
    private static String reposDir = workdir + "collect_output/repos";   // store all the repos
    private static String outputDir = workdir + "collect_output/output";
    
    @Test
    public void collectTest() throws Exception {
        String list_file = "collect_output/repo_list.csv";

        HashMap<String, String> repos = new HashMap<>();
        Client.addReposFromText(list_file, repos);
        System.out.println(repos);
        repos.forEach((projectName, url) -> {
            String repoPath = PathUtils.getFileWithPathSegment(reposDir, projectName);                      // store the specific repo
            String outputConflictPath = PathUtils.getFileWithPathSegment(outputDir, "conflictFiles");       // store all conflict files during collecting
            // String outputJsonPath = PathUtils.getFileWithPathSegment(outputDir, "mergeTuples");             // store output tuples
            // String filteredTuplePath = PathUtils.getFileWithPathSegment(outputDir, "filteredTuples");       // store filtered tuples
            try {
                Client.collectMergeConflict(repoPath, projectName, url, outputConflictPath, Client.allowedExtensions);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }


    @Test
    public void nativeRepoTest() {
        String projectPath = "collect_output/native_test_repo";
        String projectName = "native_test_repo";
        String url = "";
        String output = "collect_output/output";
        try {
            Client.collectMergeConflict(projectPath, projectName, url, output, Set.of("txt"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
