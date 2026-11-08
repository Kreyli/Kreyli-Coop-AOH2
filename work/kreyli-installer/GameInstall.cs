using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Threading;

public sealed class GameLocation { public string path; }
public sealed class PackageFile { public string path,sha256; public long size; }
public sealed class GamePackage { public string sha256; public PackageFile[] files; }
internal static class GameInstall {
 internal static string IOPath(string path){if(path.Length<240||path.StartsWith(@"\\?\"))return path;return path.StartsWith(@"\\")?@"\\?\UNC\"+path.Substring(2):@"\\?\"+path;}
 internal static readonly ManualResetEventSlim Gate=new ManualResetEventSlim(true);
 internal static bool Paused;
 internal static void Pause(bool pause){Paused=pause;if(pause)Gate.Reset();else Gate.Set();}
 internal static string DirectoryPath {
  get {string config=Path.Combine(Launcher.Root,"game-location.json");if(!File.Exists(config))return Launcher.Root;
   var location=Launcher.Json.Deserialize<GameLocation>(File.ReadAllText(config));
   if(location==null||string.IsNullOrWhiteSpace(location.path))throw new IOException("Не указан путь к игре");return Path.GetFullPath(location.path);}
 }
 internal static bool Installed {get{return File.Exists(IOPath(Path.Combine(DirectoryPath,"game.jar")));}}
 internal static GamePackage Package(){
  string path=Path.Combine(Launcher.Root,"packages","game-package.json");
  if(!File.Exists(path))throw new IOException("Не найден пакет игры. Повторите установку полного комплекта Kreyli.");
  var parser=new System.Web.Script.Serialization.JavaScriptSerializer{MaxJsonLength=64*1024*1024};
  var result=parser.Deserialize<GamePackage>(File.ReadAllText(path));
  if(result==null||result.files==null||result.files.Length==0||result.files.Length>500000)throw new IOException("Некорректный пакет игры");
  var unique=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
  foreach(var f in result.files){if(f==null||f.size<0||f.sha256==null||f.sha256.Length!=64||!unique.Add(f.path))throw new IOException("Некорректный список файлов");Safe(Launcher.Root,f.path,false);}
  return result;
 }
 internal static string Safe(string root,string name,bool checkLinks=true,HashSet<string> checkedDirectories=null){
  if(string.IsNullOrEmpty(name)||name.Contains("\\")||name.Contains(":")||name.StartsWith("/")||name.Split('/').Any(p=>p=="."||p==".."||p.Length==0||p.EndsWith(".")||p.EndsWith(" ")))throw new IOException("Недопустимый путь пакета");
  string first=name.Split('/')[0];if(name!="game.jar"&&!new[]{"game","map","music","sounds","UI","lwjgl"}.Contains(first))throw new IOException("Пакет не может изменять "+name);
  string basePath=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar),full=Path.GetFullPath(Path.Combine(basePath,name));
  if(!full.StartsWith(basePath+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new IOException("Путь вне папки игры");
  if(checkLinks)for(string p=full;p!=null&&p.Length>=basePath.Length;p=Path.GetDirectoryName(p)){
   if(p!=full&&checkedDirectories!=null&&checkedDirectories.Contains(p))break;
   string checkedPath=IOPath(p);if((File.Exists(checkedPath)||Directory.Exists(checkedPath))&&(File.GetAttributes(checkedPath)&FileAttributes.ReparsePoint)!=0)throw new IOException("Ссылки в папке игры не поддерживаются");
   if(p!=full&&checkedDirectories!=null)checkedDirectories.Add(p);
  }
  return IOPath(full);
 }
 internal static void Install(string destination,Action<int,string> progress){
  var package=Package();destination=Path.GetFullPath(destination);
  if(Directory.Exists(IOPath(destination))&&Directory.EnumerateFileSystemEntries(IOPath(destination)).Any()&&!File.Exists(IOPath(Path.Combine(destination,".kreyli-game"))))throw new IOException("Выберите новую пустую папку. Чужие файлы не будут перезаписаны.");
  string source=RemoteGame.Prepare(package,progress);
  Directory.CreateDirectory(IOPath(destination));File.WriteAllText(IOPath(Path.Combine(destination,".kreyli-game")),"Kreyli Coop AOH2");
  long total=package.files.Sum(f=>f.size),done=0;byte[] buffer=new byte[1048576];var checkedDirectories=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
  foreach(var file in package.files){Gate.Wait();string inputFile=Safe(source,file.path,true,checkedDirectories),target=Safe(destination,file.path,true,checkedDirectories);Directory.CreateDirectory(Path.GetDirectoryName(target));
   if(!File.Exists(inputFile)||new FileInfo(inputFile).Length!=file.size)throw new IOException("Архив не содержит нужный файл: "+file.path);
   if(File.Exists(target)&&new FileInfo(target).Length==file.size&&Launcher.Hash(target).Equals(file.sha256,StringComparison.OrdinalIgnoreCase)){done+=file.size;continue;}
   string temp=target+".kreyli-part";
   try{using(var digest=System.Security.Cryptography.SHA256.Create()){using(var input=File.OpenRead(inputFile))using(var output=new FileStream(temp,FileMode.Create,FileAccess.Write,FileShare.None,4096)){
    int read;while((read=input.Read(buffer,0,buffer.Length))>0){Gate.Wait();output.Write(buffer,0,read);digest.TransformBlock(buffer,0,read,null,0);done+=read;progress((int)(done*100/Math.Max(1,total)),"Установка · "+(done/1048576)+" / "+(total/1048576)+" МБ");}}
    digest.TransformFinalBlock(new byte[0],0,0);if(!BitConverter.ToString(digest.Hash).Replace("-","").Equals(file.sha256,StringComparison.OrdinalIgnoreCase))throw new IOException("Ошибка проверки "+file.path);}
    if(File.Exists(target))File.Replace(temp,target,null);else File.Move(temp,target);
   }finally{if(File.Exists(temp))File.Delete(temp);}
  }
  File.WriteAllText(Path.Combine(Launcher.Root,"game-location.json"),Launcher.Json.Serialize(new GameLocation{path=destination}));
  progress(100,"Игра установлена");
 }
 internal static void Remove(Action<int,string> progress){
  string root=DirectoryPath;
  if(!File.Exists(IOPath(Path.Combine(root,".kreyli-game")))&&!File.Exists(IOPath(Path.Combine(root,".kreyli-install"))))throw new IOException("Удалять можно только отдельную установку Kreyli");
  var files=Package().files;
  // Delete only package-owned files. Saves, settings, launcher, mods and unrelated files remain.
  var checkedDirectories=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
  for(int i=0;i<files.Length;i++){string target=Safe(root,files[i].path,true,checkedDirectories);if(File.Exists(target))File.Delete(target);if(i%100==0)progress(i*100/files.Length,"Удаление игры…");}
  progress(100,"Игра удалена. Лаунчер и сохранения сохранены.");
 }
 internal static void Verify(Action<int,string> progress){var files=Package().files;string root=DirectoryPath;int errors=0;var checkedDirectories=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
  for(int i=0;i<files.Length;i++){Gate.Wait();var f=files[i];string p=Safe(root,f.path,true,checkedDirectories);if(!File.Exists(p)||new FileInfo(p).Length!=f.size||!Launcher.Hash(p).Equals(f.sha256,StringComparison.OrdinalIgnoreCase))errors++;if(i%100==0)progress(i*100/files.Length,"Проверка файлов…");}
  if(errors>0)throw new IOException("Повреждены или отсутствуют файлы: "+errors+". Переустановите игру через меню лаунчера.");progress(100,"Все файлы игры проверены");
 }
}
