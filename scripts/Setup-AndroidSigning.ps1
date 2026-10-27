[CmdletBinding()]
param([string]$KeystoreToRestore)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repoRoot=Split-Path -Parent $PSScriptRoot
$privateRoot=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) '1ByteLabs/ZLPlayMobile/signing'
$backupRoot=Join-Path $repoRoot 'local-notes/signing-backup'
$resultPath=Join-Path $repoRoot 'tools/android-signing-result.json'
$aliasName='zlplay-mobile'
$store=Join-Path $privateRoot 'zlplay-mobile-release.p12'
$keytool=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/keytool.exe'}else{Join-Path $env:ProgramFiles 'Android/Android Studio/jbr/bin/keytool.exe'}
function Reveal-InMemory([Security.SecureString]$value){$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($value);try{[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}}
try {
    New-Item -ItemType Directory -Force -Path $privateRoot,$backupRoot | Out-Null
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    foreach($directory in @($privateRoot,$backupRoot)){& icacls.exe $directory /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null;if($LASTEXITCODE -ne 0){throw 'Falha ao proteger a pasta de assinatura.'}}
    if($LASTEXITCODE -ne 0){throw 'Nao foi possivel proteger a pasta privada.'}
    if(!(Test-Path -LiteralPath $keytool)){throw 'Configure JAVA_HOME para o JDK do Android Studio.'}
    if((Test-Path -LiteralPath $store) -and !$KeystoreToRestore){throw 'Uma chave ja existe. Nao sera substituida. Use Build-AndroidRelease.ps1.'}
    Write-Host 'ZLPlay Mobile - assinatura de distribuicao' -ForegroundColor Cyan
    Write-Host 'Digite a senha SOMENTE nesta janela. Ela nao e enviada ao chat.'
    Write-Host 'O backup nao contera a senha. Guarde-a para recuperar a chave apos formatar.'
    $secure=Read-Host 'Senha da chave (minimo 8 caracteres)' -AsSecureString
    $plain=Reveal-InMemory $secure
    if($plain.Length -lt 8){throw 'Use pelo menos 8 caracteres.'}
    if(!$KeystoreToRestore){$confirm=Read-Host 'Repita a senha' -AsSecureString;$check=Reveal-InMemory $confirm;if($plain -cne $check){throw 'As senhas nao coincidem.'};$check=$null;$confirm.Dispose()}
    $env:ZLPLAY_TEMP_SIGN_PASS=$plain
    $stage=Join-Path $privateRoot ('pending-'+[Guid]::NewGuid().ToString('N')+'.p12')
    if($KeystoreToRestore){Copy-Item -LiteralPath (Resolve-Path -LiteralPath $KeystoreToRestore).Path -Destination $stage}
    else{& $keytool -genkeypair -alias $aliasName -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 -storetype PKCS12 -keystore $stage -storepass:env ZLPLAY_TEMP_SIGN_PASS -keypass:env ZLPLAY_TEMP_SIGN_PASS -dname 'CN=ZLPlay Mobile, O=1Byte Labs, C=BR' -noprompt *> (Join-Path $privateRoot 'keytool.log');if($LASTEXITCODE -ne 0){throw 'Falha ao criar a chave; confira keytool.log na pasta privada.'}}
    $certificate=Join-Path $privateRoot ('pending-cert-'+[Guid]::NewGuid().ToString('N')+'.der')
    & $keytool -exportcert -alias $aliasName -keystore $stage -storepass:env ZLPLAY_TEMP_SIGN_PASS -file $certificate *> (Join-Path $privateRoot 'certificate-export.log')
    if($LASTEXITCODE -ne 0){throw 'Nao foi possivel validar a chave. Verifique a senha e o alias zlplay-mobile.'}
    $fingerprint=(Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant()
    if(Test-Path -LiteralPath $store){if((Get-FileHash -LiteralPath $store).Hash -ne (Get-FileHash -LiteralPath $stage).Hash){throw 'Ja existe outra chave local. Nada foi substituido.'};Remove-Item -LiteralPath $stage}else{Move-Item -LiteralPath $stage -Destination $store}
    $finalCertificate=Join-Path $privateRoot 'release-certificate.der'
    Move-Item -LiteralPath $certificate -Destination $finalCertificate -Force
    $certificate=$finalCertificate
    $secure | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $privateRoot 'password.dpapi') -Encoding utf8
    $metadata=[ordered]@{applicationId='com.onebytelabs.zlplay.mobile';alias=$aliasName;keystore='zlplay-mobile-release.p12';certificateSha256=$fingerprint;algorithm='RSA-4096 / SHA256withRSA';passwordIncludedInBackup=$false}
    $metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $privateRoot 'metadata.json') -Encoding utf8
    $bundle=Join-Path $backupRoot 'ZLPlay-Mobile-assinatura-PRIVADO'
    New-Item -ItemType Directory -Force -Path $bundle | Out-Null
    Copy-Item -LiteralPath $store,$certificate,(Join-Path $privateRoot 'metadata.json') -Destination $bundle -Force
    @'
ZLPLAY MOBILE - BACKUP PRIVADO DE ASSINATURA
NAO PUBLICAR NO GITHUB NEM ANEXAR A UMA RELEASE.

Guarde este pacote no seu Drive e mantenha sua senha recuperavel.
Ele contem a chave PKCS12 protegida pela senha escolhida por voce.
A senha NAO esta incluida; a copia protegida pelo Windows (DPAPI) nao e portavel e nao esta neste backup.
Alias: zlplay-mobile
Application ID: com.onebytelabs.zlplay.mobile

Depois de formatar ou trocar de computador:
1. Restaure o projeto e o Android SDK/JDK.
2. Extraia este ZIP para uma pasta privada.
3. Execute, na raiz do projeto:
   pwsh -File scripts/Setup-AndroidSigning.ps1 -KeystoreToRestore "CAMINHO/zlplay-mobile-release.p12"
4. Digite localmente a mesma senha. O script valida a chave e recria a protecao local.
5. Gere atualizacoes com scripts/Build-AndroidRelease.ps1.

Nao gere outra chave para atualizar o mesmo aplicativo.
O certificado publico e metadata.json permitem conferir a identidade da assinatura.
A copia deste computador so pode ser recuperada com este arquivo E a senha.
'@ | Set-Content -LiteralPath (Join-Path $bundle 'LEIA-ME-RECUPERACAO.txt') -Encoding utf8
    $zip=Join-Path $backupRoot 'ZLPlay-Mobile-assinatura-PRIVADO.zip'
    Compress-Archive -LiteralPath (Get-ChildItem -LiteralPath $bundle -File).FullName -DestinationPath $zip -Force
    [ordered]@{status='ready';backup=$zip;privateDirectory=$privateRoot;certificateSha256=$fingerprint} | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding utf8
    Write-Host "Concluido. Backup privado: $zip" -ForegroundColor Green
} catch {
    [ordered]@{status='needs-attention';message=$_.Exception.Message} | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding utf8
    Write-Host $_.Exception.Message -ForegroundColor Red
} finally {
    $env:ZLPLAY_TEMP_SIGN_PASS=$null;$plain=$null
    if(Get-Variable secure -ErrorAction SilentlyContinue){$secure.Dispose()}
    if((Get-Variable stage -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath $stage)){Remove-Item -LiteralPath $stage}
}
Read-Host 'Pressione Enter para fechar' | Out-Null
