package nju.merge.client;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
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
            try {
                Client.collectMergeConflict(repoPath, projectName, url, outputConflictPath, Client.allowedExtensions);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }


    @Test
    public void nativeRepoTest() {
        // String repoList = workdir + "/list.txt";

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

    @Test
    public void mergeTest() {
        String projectPath = "collect_output/repos/cucumber-ruby";

        try {
            // Open the existing repository located at 'projectPath'
            File repoDir = new File(projectPath);
            Git git = Git.open(repoDir);
            Repository repo = git.getRepository();

            // Define the two commits you want to merge
            String oursHash = "c0aea16b"; // 'ours' commit
            String theirsHash = "b6579";  // 'theirs' commit

            // Checkout to 'ours' commit
            git.checkout().setName(oursHash).call();

            // Resolve the ObjectIds for the commits
            ObjectId theirsId = repo.resolve(theirsHash);

            // Prepare the merge command
            MergeCommand merge = git.merge();
            merge.include(theirsId);               // Include 'theirs' commit in the merge

            // Execute the merge
            MergeResult result = merge.call();

            // Handle the merge result
            if (result.getMergeStatus().isSuccessful()) {
                System.out.println("Merge was successful!");
            } else if (result.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING)) {
                System.out.println("Merge resulted in conflicts.");
                // retrieve and handle conflicts here if needed
                Map<String, int[][]> conflicts = result.getConflicts();
                if (conflicts != null) {
                    for (String path : conflicts.keySet()) {
                        System.out.println("Conflicts in file: " + path);
                    }
                }
            } else {
                System.out.println("Merge failed with status: " + result.getMergeStatus());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
