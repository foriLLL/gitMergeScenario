package nju.merge.core.align;

import nju.merge.core.GitService;
import nju.merge.entity.ConflictChunk;
import nju.merge.entity.ConflictFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DeepMergeAligner extends Aligner{
    private static final Logger logger = LoggerFactory.getLogger(DeepMergeAligner.class);

    public static void getResolutions(ConflictFile cf) {
        try {
            // 1. 通过 base ours theirs 获得 merged
            String[] merged = GitService.getMergedContent(cf.oursContent, cf.theirsContent, cf.baseContent);
            cf.mergedContent = merged;
            // 2. 扫描 merged，遇到一个 chunk 收集一个
            for (int i = 0; i < merged.length;) {
                String line = merged[i];
                if (line.equals("<<<<<<< OURS")) {
                    ConflictChunk cc = new ConflictChunk();
                    cc.startLine = i;
                    cc.resolvedCommitHash = cf.resolvedCommitHash;
                    cc.repositoryName = cf.repositoryName;
                    cc.filePath = cf.filePath;

                    int start = i + 1;
                    boolean illegalCase = false;
                    while (!merged[i].equals("||||||| BASE")) {
                        i++;
                        if (i == merged.length) {
                            illegalCase = true;
                            break;
                        }
                    }
                    if (illegalCase) break;
                    String[] chunkOurs = Arrays.copyOfRange(merged, start, i++);
                    start = i;
                    while (!merged[i].equals("=======")) {
                        i++;
                        if (i == merged.length) {
                            illegalCase = true;
                            break;
                        }
                    }
                    if (illegalCase) break;
                    String[] chunkBase = Arrays.copyOfRange(merged, start, i++);
                    start = i;
                    while (!merged[i].equals(">>>>>>> THEIRS")) {
                        i++;
                        if (i == merged.length) {
                            illegalCase = true;
                            break;
                        }
                    }
                    if (illegalCase) break;
                    String[] chunkTheirs = Arrays.copyOfRange(merged, start, i++);
                    cc.endLine = i;
                    cc.ours = chunkOurs;
                    cc.base = chunkBase;
                    cc.theirs = chunkTheirs;
                    cf.addConflictChunk(cc);
                    continue;
                }
                i++;
            }

            
            // 3. 遍历 chunk 获取 resolution
            int len_after = cf.resolvedContent.length + 2;

            String[] truthPadded = new String[len_after];
            truthPadded[0] = "<Begin Marker Here>";
            System.arraycopy(cf.resolvedContent, 0, truthPadded, 1, cf.resolvedContent.length);
            truthPadded[len_after - 1] = "<End Marker Here>";

            String[] reversedTruthPadded = new String[len_after];
            for (int i = 0; i < len_after; i++) {
                reversedTruthPadded[i] = truthPadded[len_after - 1 - i];
            }

            for (ConflictChunk cc: cf.conflictChunks) {

                // suffix 在 truthPadded 中开始下标（注意 padding 影响，所以需要从 truthPadded 中抽取 resolution）
                String[] subArr_eos = new String[cf.mergedContent.length - cc.endLine + 1];
                System.arraycopy(cf.mergedContent, cc.endLine, subArr_eos, 0, cf.mergedContent.length - cc.endLine);
                subArr_eos[subArr_eos.length - 1] = "<End Marker Here>";
                int sffxIdx = minimalUniquePrefix(subArr_eos, truthPadded);
                if (sffxIdx == -1) continue;


                // prefix 在 truthPadded 中开始下标（注意 padding 影响，所以需要从 truthPadded 中抽取 resolution）
                // 1. reverse truthPadded
                // 2. construct (reversed mergedContent[:cc.startline]) + ['bos']
                String[] subArr_bos = new String[cc.startLine + 1];
                for (int i = 0; i < cc.startLine; i++) {
                    subArr_bos[i] = cf.mergedContent[cc.startLine - 1 - i];
                }
                subArr_bos[subArr_bos.length - 1] = "<Begin Marker Here>";
                int prfxIdx = minimalUniquePrefix(subArr_bos, reversedTruthPadded);
                if (prfxIdx == -1) continue;


                // extract resolution
                if (len_after - prfxIdx <= sffxIdx) {
                    cc.resolution = Arrays.copyOfRange(truthPadded, len_after - prfxIdx, sffxIdx);
                }
            }
        } catch (IOException e) {
            logger.error("error met while getting diff3 merged content: ", e);
        }
    }

    private static int minimalUniquePrefix(String[] x, String[] y) {
        // 找到 x 在 y 中唯一出现的最小前缀的在 y 中出现的开始位置
        if (x.length == 0) {
            return -1;
        }
        // init, construct init candidates
        Set<Integer> cands = new HashSet<>();
        for (int i = 0; i < y.length; i++) {
            if (x[0].equals(y[i])) {
                cands.add(i);
            }
        }

        int offset = 0;
        while (cands.size() > 1) {
            if (++offset == x.length) return -1;
            Iterator<Integer> iter = cands.iterator();
            while (iter.hasNext()) {
                int idx = iter.next();
                if (idx + offset >= y.length || !y[idx + offset].equals(x[offset])) {
                    iter.remove();
                }
            }
        }
        if (cands.isEmpty()) return -1;
        return cands.iterator().next();
    }
}
