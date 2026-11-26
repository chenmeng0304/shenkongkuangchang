# 从腾讯云服务器推送 GitHub（本机直连 github.com 超时的时候用）
#
# 用法（在 D:\skkc 目录下）：
#   .\push-via-server.ps1              # 会提示输入 GitHub Personal Access Token
#
# 为什么不直接在本机 push：本机到 github.com:443 连接超时（TLS 握手不回来），
# 而服务器 124.222.119.2 访问 github.com 只要 1 秒左右。
param(
    [string]$Server = 'root@124.222.119.2',
    [string]$Key = 'D:\game\.deploy\id_ed25519',
    [string]$Repo = 'https://github.com/chenmeng0304/shenkongkuangchang.git',
    [string]$Branch = 'master'
)

$ErrorActionPreference = 'Stop'
$ident = @('-i', $Key, '-o', 'IdentitiesOnly=yes', '-o', 'StrictHostKeyChecking=accept-new')

# 1) 取 token（不回显、不进命令历史）
$sec = Read-Host -AsSecureString '请粘贴 GitHub Personal Access Token（输入不回显）'
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
try {
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}
if (-not $token) { throw '没有拿到 token' }

$work = Join-Path $env:TEMP 'skkc-push'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Path $work | Out-Null

# 2) 打包（含 .git，排除构建产物）
Write-Host '[1/4] 打包仓库…'
$tgz = Join-Path $work 'skkc.tar.gz'
Push-Location $PWD
try {
    & tar -czf $tgz --exclude=target --exclude=node_modules -C (Split-Path $PWD -Parent) (Split-Path $PWD -Leaf)
    if ($LASTEXITCODE -ne 0) { throw '打包失败' }
} finally { Pop-Location }
Write-Host ("      包大小 {0:N1} MB" -f ((Get-Item $tgz).Length / 1MB))

# 3) 上传 + 在服务器上 push
Write-Host '[2/4] 上传到服务器…'
& scp -P 22 @ident $tgz "${Server}:/tmp/skkc.tar.gz"
if ($LASTEXITCODE -ne 0) { throw '上传失败' }

Write-Host '[3/4] 服务器解包…'
$remote = @"
set -e
rm -rf /tmp/skkc && mkdir -p /tmp/skkc
tar -xzf /tmp/skkc.tar.gz -C /tmp/skkc --strip-components=1
cd /tmp/skkc
git -c safe.directory=/tmp/skkc log --oneline -3
"@
& ssh @ident $Server $remote
if ($LASTEXITCODE -ne 0) { throw '服务器解包失败' }

Write-Host '[4/4] 推送…'
# token 只用于这一次 push，推完立刻把 remote 还原成无凭据地址
$push = @"
set -e
cd /tmp/skkc
git remote set-url origin 'https://x-access-token:$token@github.com/chenmeng0304/shenkongkuangchang.git'
git push origin HEAD:$Branch 2>&1 | tail -3
git remote set-url origin '$Repo'
git remote -v
"@
& ssh @ident $Server $push
$code = $LASTEXITCODE

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
if ($code -ne 0) { throw "推送失败（exit $code），看上面服务器输出" }
Write-Host ''
Write-Host '推送完成 ✔ 去云托管控制台重新部署即可' -ForegroundColor Green
