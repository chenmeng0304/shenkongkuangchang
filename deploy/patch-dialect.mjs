/* 数据库不可达时也让应用起来：显式指定 Hibernate 方言 + 关闭启动期元数据探测 */
import fs from 'node:fs';

const p = 'D:/skkc/src/main/resources/application.yml';
let s = fs.readFileSync(p, 'utf8');

if (!s.includes('database-platform')) {
  s = s.replace(
    '  jpa:\n    open-in-view: false\n',
    '  jpa:\n    open-in-view: false\n' +
    '    # 显式指定方言：否则 Hibernate 启动时要连数据库读元数据，连不上就直接退出\n' +
    '    # （云端表现为 80 端口 connection refused，看不到真正原因）\n' +
    '    database-platform: ${JPA_DIALECT:org.hibernate.dialect.MySQLDialect}\n',
  );
  fs.writeFileSync(p, s, 'utf8');
  console.log('✓ 已加 database-platform');
} else {
  console.log('已有 database-platform');
}

// 测试环境继续用 H2 方言
const t = 'D:/skkc/src/test/resources/application-test.yml';
if (fs.existsSync(t)) {
  let ts = fs.readFileSync(t, 'utf8');
  if (!ts.includes('database-platform')) {
    ts = ts.replace('  jpa:\n', '  jpa:\n    database-platform: org.hibernate.dialect.H2Dialect\n');
    fs.writeFileSync(t, ts, 'utf8');
    console.log('✓ 测试配置已指定 H2 方言');
  } else {
    console.log('测试配置已有方言');
  }
}

console.log('\n--- jpa 段 ---');
console.log(s.split('\n').slice(29, 42).join('\n'));
