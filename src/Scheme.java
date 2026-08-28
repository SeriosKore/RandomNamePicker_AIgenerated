/**
 * 预设方案模型（public：插件 API 的一部分）。
 * 每个方案绑定一种抽取模式：name_list / number / seat。
 */
public class Scheme {
    private String name;
    private String type;

    public Scheme(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }
}
