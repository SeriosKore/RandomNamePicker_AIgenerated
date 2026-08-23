import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.io.File;

/**
 * 核心逻辑自检脚本（在临时目录中运行，避免污染仓库数据）。
 * 覆盖：
 *  1. 名单保存/新增/删除/修改 触发 modification_log.txt 追加记录；
 *  2. 多人点名去重抽取（pickDistinct）逻辑；
 *  3. 单次抽取数量配置（pickCount）读写。
 */
public class SelfCheck {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("== RandomNamePicker 自检开始 ==");

        // ---------- 1. 名单修改日志 ----------
        NameManager nameManager = new NameManager();
        String scheme = "自检方案";

        nameManager.saveNamesForScheme(scheme, new ArrayList<>(Arrays.asList("张三", "李四", "王五")));
        String logAfterSave = ModificationLogManager.readLog();
        check("新增名单写入修改日志", logAfterSave.contains("新增") && logAfterSave.contains("张三"));

        nameManager.addNameToScheme(scheme, "赵六");
        String logAfterAdd = ModificationLogManager.readLog();
        check("追加姓名写入修改日志", logAfterAdd.contains("赵六"));

        nameManager.removeNameFromScheme(scheme, "李四");
        String logAfterRemove = ModificationLogManager.readLog();
        check("删除姓名写入修改日志", logAfterRemove.contains("删除") && logAfterRemove.contains("李四"));

        List<String> changed = new ArrayList<>(Arrays.asList("张三", "王五改", "赵六"));
        nameManager.saveNamesForScheme(scheme, changed);
        String logAfterModify = ModificationLogManager.readLog();
        check("修改姓名记录修改前后对比", logAfterModify.contains("修改前=王五") && logAfterModify.contains("修改后=王五改"));

        check("修改日志文件已自动创建", new File(ModificationLogManager.LOG_FILE_PATH).exists());
        check("修改日志包含时间戳", logAfterModify.matches("(?s).*\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\].*"));

        // 名单加密往返
        List<String> loaded = nameManager.loadNamesForScheme(scheme);
        check("名单加密保存后可正确读取", loaded.size() == 3 && loaded.contains("张三") && loaded.contains("王五改"));

        // ---------- 2. 多人点名去重抽取 ----------
        List<String> pool = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            pool.add("学生" + i);
        }
        SecureRandom random = new SecureRandom();
        List<String> winners = NamePickerApp.pickDistinct(pool, 5, random);
        check("多人抽取数量正确", winners.size() == 5);
        check("多人抽取结果去重", new HashSet<>(winners).size() == 5);
        check("多人抽取结果为名单子集", pool.containsAll(winners));

        List<String> all = NamePickerApp.pickDistinct(pool, 100, random);
        check("抽取数量不超过名单总人数", all.size() == 10);

        // ---------- 3. 单次抽取数量配置 ----------
        ConfigManager.setPickCount(3);
        check("单次抽取数量配置写入", ConfigManager.getPickCount() == 3);
        ConfigManager.setPickCount(1);
        check("单次抽取数量配置重置", ConfigManager.getPickCount() == 1);

        // ---------- 汇总 ----------
        System.out.println();
        if (failures == 0) {
            System.out.println("自检通过：全部检查项通过。");
        } else {
            System.out.println("自检失败：" + failures + " 项未通过。");
            System.exit(1);
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("[通过] " + name);
        } else {
            System.out.println("[失败] " + name);
            failures++;
        }
    }
}
