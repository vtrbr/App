# ZIP do repositório App — commit `fb34661`

O GitHub limita arquivos individuais a 100 MB. Por isso, o ZIP completo foi dividido em partes de 90 MB.

## Reconstruir o ZIP

Linux/macOS:

```bash
cat tedflix-App-current-fb34661.zip.part-* > tedflix-App-current-fb34661.zip
sha256sum -c SHA256SUMS
```

Windows PowerShell:

```powershell
cmd /c copy /b tedflix-App-current-fb34661.zip.part-* tedflix-App-current-fb34661.zip
(Get-FileHash tedflix-App-current-fb34661.zip -Algorithm SHA256).Hash
```

O hash esperado está em `SHA256SUMS`.
