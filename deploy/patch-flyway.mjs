/* 关掉 Spring 自带的 Flyway 自动迁移（改由 FlywayMigrator 手动执行） */
import fs from 'node:fs';
const p = 'D:/skkc/src/main/resources/application.yml';
let s = fs.readFileSync(p, 'utf8');
if (/  flyway:\n    enabled: true/.test(s)) {
  s = s.replace('  flyway:\n    enabled: true',
    '  flyway:\n    # 关掉 Spring 自带的 Flyway 自动迁移：改由 FlywayMigrator 手动跑，失败也继续启动\n    enabled: false');
  fs.writeFileSync(p, s, 'utf8');
  console.log('✓ 已关闭 Spring Flyway 自动迁移');
} else {
  console.log('当前 flyway 配置：');
}
console.log(s.split('\n').filter((l) => /flyway|enabled|baseline/.test(l)).join('\n'));
