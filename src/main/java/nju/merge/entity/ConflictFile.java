package nju.merge.entity;

public class ConflictFile {
    public String[] baseContent;      // 基准版本的文件内容
    public String[] oursContent;      // 我方版本的文件内容
    public String[] theirsContent;    // 对方版本的文件内容
    public String[] mergedContent;    // 合并后的文件内容（可能包含冲突标记）
    public String[] resolvedContent;  // 解决冲突后的文件内容

    public String resolvedCommitHash;// 合并场景的提交哈希值
    public String baseHash;         // 基准版本的哈希值
    public String oursHash;         // 我方版本的哈希值
    public String theirsHash;       // 对方版本的哈希值

    public String filePath;         // 文件路径
    public String repositoryName;   // 来源的仓库

    public ConflictFile(String[] baseContent, String[] oursContent, String[] theirsContent, String[] mergedContent,
                        String[] resolvedContent, String resolvedCommitHash, String baseHash, String oursHash,
                        String theirsHash, String filePath, String repositoryName) {
        this.baseContent = baseContent;
        this.oursContent = oursContent;
        this.theirsContent = theirsContent;
        this.mergedContent = mergedContent;
        this.resolvedContent = resolvedContent;
        
        this.resolvedCommitHash = resolvedCommitHash;
        this.baseHash = baseHash;
        this.oursHash = oursHash;
        this.theirsHash = theirsHash;
        this.filePath = filePath;
        this.repositoryName = repositoryName;
    }

    @Override
    public String toString() {
        return "ConflictFile{" +
                "baseContent='" + baseContent + '\'' +
                ", oursContent='" + oursContent + '\'' +
                ", theirsContent='" + theirsContent + '\'' +
                ", mergedContent='" + mergedContent + '\'' +
                ", resolvedContent='" + resolvedContent + '\'' +
                ", resolvedCommitHash='" + resolvedCommitHash + '\'' +
                ", baseHash='" + baseHash + '\'' +
                ", oursHash='" + oursHash + '\'' +
                ", theirsHash='" + theirsHash + '\'' +
                ", filePath='" + filePath + '\'' +
                '}';
    }
}