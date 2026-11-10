using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;
[assembly: AssemblyTitle("Kreyli Coop AOH2")]
[assembly: AssemblyProduct("Kreyli Coop AOH2")]
[assembly: AssemblyVersion("1.1.14.0")]
public sealed class UpdateFile { public string path,url,sha256; public long size; }
public sealed class UpdateManifest { public string version; public UpdateFile[] files; }
public sealed class Channel { public string manifestUrl; }
public sealed class ReleaseEntry {
 public string tag_name,body;
 public bool draft,prerelease;
}
internal static class Launcher {
 static Launcher(){AppContext.SetSwitch("Switch.System.IO.UseLegacyPathHandling",false);AppContext.SetSwitch("Switch.System.IO.BlockLongPaths",false);}
 internal const string Brand="Kreyli Coop AOH2",GameHash="db144f0ddb7e62de92ad971eb7bc0b9a66b57cabbfe70b9dcda7d2a70204704c";
 internal static readonly JavaScriptSerializer Json=new JavaScriptSerializer{MaxJsonLength=2097152};
 internal static string Root;
 internal static Action<int,string> TransferProgress;
 internal static string Quote(string value){var b=new StringBuilder("\"");int n=0;foreach(char c in value){if(c=='\\'){n++;continue;}if(c=='\"'){b.Append('\\',n*2+1).Append(c);n=0;continue;}b.Append('\\',n).Append(c);n=0;}return b.Append('\\',n*2).Append('"').ToString();}
 internal static string Hash(string path){using(var s=SHA256.Create())using(var f=File.OpenRead(GameInstall.IOPath(path)))return BitConverter.ToString(s.ComputeHash(f)).Replace("-","").ToLowerInvariant();}
 static string MutexName(string root){using(var s=SHA256.Create())return "Local\\Kreyli-"+BitConverter.ToString(s.ComputeHash(Encoding.UTF8.GetBytes(Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar).ToLowerInvariant()))).Replace("-","");}
 [STAThread] public static int Main(string[] args){
  Root=AppDomain.CurrentDomain.BaseDirectory;
  try{
   if(args.Length==4&&args[0]=="--apply-update")return ApplyAndRestart(args[1],args[2],int.Parse(args[3]));
   Directory.SetCurrentDirectory(Root);bool acquired;
   using(var gate=new Mutex(true,MutexName(Root),out acquired)){
    if(!acquired){MessageBox.Show("Лаунчер уже открыт.",Brand);return 0;}
    if(args.Contains("--check")){CheckGame();File.WriteAllText("launcher-check.txt","PASS: bundled runtime, required archives and pinned game hash\r\n");return 0;}
    Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);Application.Run(new LauncherWindow(args));
   }return 0;
  }catch(Exception ex){try{File.WriteAllText(Path.Combine(Root,"launcher-error.txt"),ex.ToString());}catch{}if(!args.Contains("--check"))MessageBox.Show(ex.Message,Brand,MessageBoxButtons.OK,MessageBoxIcon.Error);return 1;}
 }
 internal static void CheckGame(bool verifyHash=true){foreach(string f in new[]{"runtime/bin/javaw.exe","shared-support.jar","shared-agent.jar","kreyli-steam.jar","lib/steamworks4j-1.9.0.jar"})if(!File.Exists(Path.Combine(Root,f)))throw new Exception("Не найден "+f+". Повторите установку.");if(verifyHash&&Hash(Path.Combine(GameInstall.DirectoryPath,"game.jar"))!=GameHash)throw new Exception("Версия game.jar не соответствует этой сборке.");}
 internal static void RunGame(string[] args){
  // The Java agent validates the pinned archive before the game loads.
  CheckGame(false);string gameRoot=GameInstall.DirectoryPath;foreach(string dir in new[]{"coop-home","coop-tmp","saves"})Directory.CreateDirectory(GameInstall.IOPath(Path.Combine(gameRoot,dir)));
  var options=new[]{"--add-exports","java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED","-javaagent:"+Path.Combine(Root,"shared-agent.jar"),"-Dkreyli.version="+typeof(Launcher).Assembly.GetName().Version.ToString(3),"-Duser.home="+Path.Combine(gameRoot,"coop-home"),"-Djava.io.tmpdir="+Path.Combine(gameRoot,"coop-tmp"),"-cp",string.Join(";",new[]{Path.Combine(Root,"kreyli-steam.jar"),Path.Combine(Root,"shared-support.jar"),Path.Combine(Root,"lib/steamworks4j-1.9.0.jar"),Path.Combine(gameRoot,"game.jar")}),"age.of.civilizations2.jakowski.lukasz.mp.KreyliBootstrap"};
  File.WriteAllText(GameInstall.IOPath(Path.Combine(gameRoot,"steam_appid.txt")),"480");
  var info=new ProcessStartInfo(Path.Combine(Root,"runtime/bin/javaw.exe"),string.Join(" ",options.Concat(args).Select(Quote))){WorkingDirectory=gameRoot,UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true};
  using(var output=new StreamWriter(Path.Combine(Root,"kreyli.out.log"),false))using(var error=new StreamWriter(Path.Combine(Root,"kreyli.err.log"),false))using(var game=new Process()){
   output.AutoFlush=true;error.AutoFlush=true;game.StartInfo=info;game.OutputDataReceived+=(s,e)=>{if(e.Data!=null)lock(output)output.WriteLine(e.Data);};game.ErrorDataReceived+=(s,e)=>{if(e.Data!=null)lock(error)error.WriteLine(e.Data);};game.Start();game.BeginOutputReadLine();game.BeginErrorReadLine();game.WaitForExit();if(game.ExitCode!=0)throw new Exception("Игра завершилась с кодом "+game.ExitCode+". Подробности: kreyli.err.log");
  }
 }
 internal static string SafePath(string root,string relative){
  if(string.IsNullOrEmpty(relative)||relative.Contains("\\")||relative.StartsWith("/")||relative.Contains(":")||relative.Split('/').Any(s=>s.Length==0||s=="."||s==".."||s.EndsWith(".")||s.EndsWith(" ")))throw new Exception("Недопустимый путь обновления: "+relative);
  string[] files={"shared-agent.jar","shared-support.jar","kreyli-steam.jar","Kreyli Coop AOH2.exe","Kreyli Coop AOH2.exe.config","README-PLAYER.txt","kreyli.ico","steam_appid.txt","lib/steamworks4j-1.9.0.jar","lib/STEAMWORKS4J-LICENSE.txt"};
  // Public updates contain only our patch, launcher and documentation; never game assets or saves.
  if(!files.Contains(relative,StringComparer.OrdinalIgnoreCase))throw new Exception("Обновление не может изменить "+relative);
  string full=Path.GetFullPath(Path.Combine(root,relative));string basePath=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
  if(!full.StartsWith(basePath+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new Exception("Путь вне установки");
  for(string dir=Path.GetDirectoryName(full);dir!=null&&dir.Length>=basePath.Length;dir=Path.GetDirectoryName(dir))if(Directory.Exists(dir)&&(File.GetAttributes(dir)&FileAttributes.ReparsePoint)!=0)throw new Exception("Ссылка в пути обновления запрещена");
  if(File.Exists(full)&&(File.GetAttributes(full)&FileAttributes.ReparsePoint)!=0)throw new Exception("Ссылка вместо файла обновления");return full;
 }
 internal static void Validate(UpdateManifest m,string root){
  Version version;if(m==null||!Version.TryParse(m.version,out version)||m.files==null||m.files.Length<1||m.files.Length>32)throw new Exception("Некорректный манифест обновления");
  var paths=new HashSet<string>(StringComparer.OrdinalIgnoreCase);long total=0;
  foreach(var f in m.files){if(f==null)throw new Exception("Пустой файл обновления");SafePath(root,f.path);if(!paths.Add(f.path)||f.sha256==null||!System.Text.RegularExpressions.Regex.IsMatch(f.sha256,"^[a-fA-F0-9]{64}$")||f.size<0||f.size>128L*1024*1024)throw new Exception("Некорректное описание файла");Uri url;if(!Uri.TryCreate(f.url,UriKind.Absolute,out url)||url.Scheme!="https"||!string.IsNullOrEmpty(url.UserInfo))throw new Exception("Обновления требуют HTTPS");total+=f.size;if(total>512L*1024*1024)throw new Exception("Пакет обновления слишком большой");}
 }
 internal static byte[] Download(string url,long limit){return DownloadCore(url,limit,true);}
 static byte[] DownloadCore(string url,long limit,bool trackTransfer){
  Uri current=new Uri(url);
  for(int redirects=0;redirects<6;redirects++){
   if(current.Scheme!="https"||!string.IsNullOrEmpty(current.UserInfo))throw new Exception("Загрузка требует HTTPS");
   var req=(HttpWebRequest)WebRequest.Create(current);req.Timeout=20000;req.ReadWriteTimeout=20000;req.AllowAutoRedirect=false;req.UserAgent="Kreyli-Coop-AOH2/1.0";
   using(var response=(HttpWebResponse)req.GetResponse()){
    int code=(int)response.StatusCode;if(code==301||code==302||code==303||code==307||code==308){current=new Uri(current,response.Headers["Location"]);continue;}
    if(response.StatusCode!=HttpStatusCode.OK||response.ContentLength>limit)throw new Exception("Неподходящий ответ сервера обновлений");
    using(var input=response.GetResponseStream())using(var memory=new MemoryStream()){byte[] block=new byte[65536];int count;long total=0;while((count=input.Read(block,0,block.Length))>0){if(trackTransfer)GameInstall.Gate.Wait();total+=count;if(total>limit)throw new Exception("Размер загрузки превышен");memory.Write(block,0,count);if(trackTransfer&&TransferProgress!=null)TransferProgress((int)(total*100/Math.Max(1,limit)),"Загрузка обновления · "+(total/1048576)+" / "+(limit/1048576)+" МБ");}return memory.ToArray();}
   }
  }throw new Exception("Слишком много перенаправлений загрузки");
 }
 internal static ReleaseEntry LatestRelease(){
  const string url="https://api.github.com/repos/Kreyli/Kreyli-Coop-AOH2/releases/latest";
  string cache=Path.Combine(Root,"release-history.json");
  try{
   ServicePointManager.SecurityProtocol=SecurityProtocolType.Tls12;
   string json=Encoding.UTF8.GetString(DownloadCore(url,2097152,false)).TrimStart('\uFEFF');
   var release=ParseLatestRelease(json);
   if(release==null)throw new IOException("Сведения об обновлении пусты");
   try{File.WriteAllText(cache,json);}catch(IOException){}catch(UnauthorizedAccessException){}
   return release;
  }catch{
   try{return File.Exists(cache)?ParseLatestRelease(File.ReadAllText(cache)):null;}
   catch{return null;}
  }
 }
 static ReleaseEntry ParseLatestRelease(string json){
  var releases=json.TrimStart().StartsWith("[",StringComparison.Ordinal)
   ?Json.Deserialize<ReleaseEntry[]>(json):new[]{Json.Deserialize<ReleaseEntry>(json)};
  return (releases??new ReleaseEntry[0]).FirstOrDefault(r=>r!=null&&!r.draft&&!r.prerelease&&!string.IsNullOrWhiteSpace(r.tag_name));
 }
 internal static bool Configured(){string path=Path.Combine(Root,"update-channel.json");if(!File.Exists(path))return false;var channel=Json.Deserialize<Channel>(File.ReadAllText(path));return channel!=null&&!string.IsNullOrWhiteSpace(channel.manifestUrl);}
 internal static UpdateManifest CheckUpdate(){
  if(!Configured())return null;var channel=Json.Deserialize<Channel>(File.ReadAllText(Path.Combine(Root,"update-channel.json")));Uri url;if(!Uri.TryCreate(channel.manifestUrl,UriKind.Absolute,out url)||url.Scheme!="https"||!string.IsNullOrEmpty(url.UserInfo))throw new Exception("Канал обновлений должен использовать HTTPS");ServicePointManager.SecurityProtocol=SecurityProtocolType.Tls12;
  var m=Json.Deserialize<UpdateManifest>(Encoding.UTF8.GetString(Download(url.AbsoluteUri,2097152)).TrimStart('\uFEFF'));Validate(m,Root);Version current=new Version(typeof(Launcher).Assembly.GetName().Version.ToString(3));string local=Path.Combine(Root,"installed-version.txt");if(File.Exists(local)&&!Version.TryParse(File.ReadAllText(local).Trim(),out current))throw new Exception("Не удалось прочитать версию установки");return new Version(m.version)>current?m:null;
 }
 internal static string Stage(UpdateManifest m,Action<string> progress){
  Validate(m,Root);string stage=Path.Combine(Root,".updates",Guid.NewGuid().ToString("N"));Directory.CreateDirectory(stage);
  for(int i=0;i<m.files.Length;i++){var f=m.files[i];progress("Загрузка обновления: "+(i+1)+" / "+m.files.Length);string target=SafePath(stage,f.path);Directory.CreateDirectory(Path.GetDirectoryName(target));File.WriteAllBytes(target,Download(f.url,f.size));if(new FileInfo(target).Length!=f.size||!Hash(target).Equals(f.sha256,StringComparison.OrdinalIgnoreCase))throw new Exception("SHA-256 не совпадает: "+f.path);}
  File.WriteAllText(Path.Combine(stage,"manifest.json"),Json.Serialize(m));return stage;
 }
 internal static void Apply(string root,string stage){
  if(!File.Exists(Path.Combine(root,".kreyli-install")))throw new Exception("Не найдена установка Kreyli");
  string expected=Path.GetFullPath(Path.Combine(root,".updates"))+Path.DirectorySeparatorChar;
  if(!Path.GetFullPath(stage).StartsWith(expected,StringComparison.OrdinalIgnoreCase))throw new Exception("Пакет вне папки обновлений");
  var m=Json.Deserialize<UpdateManifest>(File.ReadAllText(Path.Combine(stage,"manifest.json")));
  Validate(m,root);
  foreach(var f in m.files){
   string source=SafePath(stage,f.path);
   if(!File.Exists(source)||new FileInfo(source).Length!=f.size||!Hash(source).Equals(f.sha256,StringComparison.OrdinalIgnoreCase))throw new Exception("Повреждён файл обновления: "+f.path);
  }
  string suffix=".kreyli-"+Guid.NewGuid().ToString("N");
  var changed=new List<string>();
  try{
   foreach(var f in m.files)ReplaceForUpdate(SafePath(root,f.path),SafePath(stage,f.path),suffix,changed);
   string versionSource=Path.Combine(stage,"version.txt");
   File.WriteAllText(versionSource,m.version);
   ReplaceForUpdate(Path.Combine(root,"installed-version.txt"),versionSource,suffix,changed);
  }catch{
   changed.Reverse();
   foreach(string target in changed){
    string backup=target+suffix+".bak";
    if(File.Exists(backup))File.Replace(backup,target,null);else File.Delete(target);
   }
   throw;
  }
  foreach(string target in changed)try{File.Delete(target+suffix+".bak");}catch(IOException){}
 }
 static void ReplaceForUpdate(string target,string source,string suffix,List<string> changed){
  Directory.CreateDirectory(Path.GetDirectoryName(target));
  string temporary=target+suffix;
  try{
   File.Copy(source,temporary);
   if(File.Exists(target))File.Replace(temporary,target,target+suffix+".bak");
   else File.Move(temporary,target);
   changed.Add(target);
  }finally{if(File.Exists(temporary))File.Delete(temporary);}
 }
 static int ApplyAndRestart(string root,string stage,int parentId){
  Root=Path.GetFullPath(root);
  try{using(var parent=Process.GetProcessById(parentId)){
   if(!parent.WaitForExit(30000))throw new Exception("Лаунчер не завершился для обновления");
  }}catch(ArgumentException){}
  bool acquired;
  using(var gate=new Mutex(true,MutexName(Root),out acquired)){
   if(!acquired)throw new Exception("Лаунчер уже открыт");
   Apply(Root,stage);
  }
  Process.Start(new ProcessStartInfo(Path.Combine(Root,"Kreyli Coop AOH2.exe")){WorkingDirectory=Root,UseShellExecute=true});
  return 0;
 }
 internal static void StartUpdater(string stage){
  string helper=Path.Combine(stage,"KreyliUpdate.exe");
  File.Copy(Assembly.GetExecutingAssembly().Location,helper,true);
  string config=Path.Combine(Root,"Kreyli Coop AOH2.exe.config");
  if(File.Exists(config))File.Copy(config,helper+".config",true);
  Process.Start(new ProcessStartInfo(helper,"--apply-update "+Quote(Root)+" "+Quote(stage)+" "+Process.GetCurrentProcess().Id){WorkingDirectory=Root,UseShellExecute=false,CreateNoWindow=true});
 }
}






