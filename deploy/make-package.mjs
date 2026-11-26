/* 打包云托管「手动上传代码包」用的 zip（≤ 2MiB） */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const SRC = 'D:/skkc';
const OUT = path.join(SRC, 'deploy', 'skkc-code.zip');
const INCLUDE = ['Dockerfile', '.dockerignore', 'pom.xml', 'settings.xml', 'container.config.json', 'src'];

if (fs.existsSync(OUT)) fs.unlinkSync(OUT);

// 用 PowerShell 的 Compress-Archive 会带路径前缀，这里用 tar 生成 zip 更可控
const args = ['-a', '-c', '-f', OUT, ...INCLUDE];
execFileSync('tar', args, { cwd: SRC, stdio: 'inherit' });

const size = fs.statSync(OUT).size;
console.log(`已生成 ${OUT}`);
console.log(`大小 ${(size / 1024).toFixed(0)} KB（限制 2 MiB）`);

// 列出内容确认
const list = execFileSync('tar', ['-tf', OUT], { encoding: 'utf8' }).split('\n').filter(Boolean);
console.log(`文件数 ${list.length}`);
console.log(list.slice(0, 8).join('\n'));
console.log('...');
const hasDocker = list.includes('Dockerfile');
const hasPom = list.includes('pom.xml');
const hasMv = list.filter((f) => f.startsWith('src/main/resources/db/migration/')).length;
console.log(`Dockerfile=${hasDocker} pom.xml=${hasPom} 迁移脚本=${hasMv} 个`);
