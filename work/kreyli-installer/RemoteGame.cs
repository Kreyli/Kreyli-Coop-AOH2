using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Text.RegularExpressions;
internal static class RemoteGame {
 internal const string ArchiveHash="12227662305da4d9343315c0c7633b6200817678b6791beb54385c30f0d5db8a";
 internal const long ArchiveSize=459573712;
 internal static string Prepare(GamePackage package,Action<int,string> progress){
  string cache=Path.Combine(Launcher.Root,"packages"),rar=Path.Combine(cache,"original-game.rar");Directory.CreateDirectory(cache);
  if(!File.Exists(rar)||new FileInfo(rar).Length!=ArchiveSize||Launcher.Hash(rar)!=ArchiveHash){
   string partial=rar+".part";Download(partial,progress);
   progress(100,"Проверка загруженного архива…");if(Launcher.Hash(partial)!=ArchiveHash)throw new IOException("Архив Google Drive изменился или повреждён. Нужна новая проверенная сборка проекта.");
   if(File.Exists(rar))File.Delete(rar);File.Move(partial,rar);
  }
  string unpack=Path.Combine(cache,"unpacked"),game=Path.Combine(unpack,"Age of History 2 Definitive Edition");
  GameInstall.Gate.Wait();Directory.CreateDirectory(unpack);progress(0,"Распаковка игры…");
  string extractor=Path.Combine(cache,"7z.exe");if(!File.Exists(extractor))throw new IOException("Не найден встроенный распаковщик");
  string includes=string.Join(" ",Array.ConvertAll(new[]{"game","map","music","sounds","UI","lwjgl"},n=>Launcher.Quote("Age of History 2 Definitive Edition/"+n+"/*")));
  var start=new ProcessStartInfo(extractor,"x "+Launcher.Quote(rar)+" -y -aoa -bso0 -bsp1 -o"+Launcher.Quote(unpack)+" "+includes){UseShellExecute=false,CreateNoWindow=true,RedirectStandardError=true,RedirectStandardOutput=true};
  string complete=Path.Combine(unpack,".complete");
  bool cached=File.Exists(complete)&&File.ReadAllText(complete)==ArchiveHash&&AssetsPresent(package,game);
  if(!cached){
   if(File.Exists(complete))File.Delete(complete);
   using(var process=Process.Start(start)){
    var errors=process.StandardError.ReadToEndAsync();var line=new System.Text.StringBuilder();int ch;
    while((ch=process.StandardOutput.Read())!=-1){
     if(ch=='\r'||ch=='\n'||ch=='\b'){
      var match=Regex.Match(line.ToString(),@"(\d{1,3})%");
      if(match.Success)progress(Math.Min(100,int.Parse(match.Groups[1].Value)),"Распаковка игры · "+match.Groups[1].Value+"%");
      line.Clear();
     }else if(line.Length<4096)line.Append((char)ch);
    }
    process.WaitForExit();string error=errors.GetAwaiter().GetResult();
    if(process.ExitCode!=0)throw new IOException("Не удалось распаковать игру (код "+process.ExitCode+"): "+error);
   }
   if(!AssetsPresent(package,game))throw new IOException("Распаковка не завершена: отсутствуют файлы игры. Повторите установку — архив уже скачан.");
   File.WriteAllText(complete,ArchiveHash);
  }
  // Original archive supplies assets; the pinned compatible network base is bundled privately.
  string network=Path.Combine(cache,"network-game.jar");if(!File.Exists(network)||Launcher.Hash(network)!=Launcher.GameHash)throw new IOException("Не найдена совместимая сетевая основа игры");
  File.Copy(network,Path.Combine(game,"game.jar"),true);
  string native=Path.Combine(cache,"native");foreach(string file in Directory.GetFiles(native,"*",SearchOption.AllDirectories)){string relative=file.Substring(native.Length+1);string target=Path.Combine(game,"lwjgl",relative);Directory.CreateDirectory(Path.GetDirectoryName(target));File.Copy(file,target,true);}return game;
 }
 internal static bool AssetsPresent(GamePackage package,string root){
  var checkedDirectories=new System.Collections.Generic.HashSet<string>(StringComparer.OrdinalIgnoreCase);
  foreach(var file in package.files){
   if(file.path=="game.jar"||file.path.StartsWith("lwjgl/",StringComparison.Ordinal))continue;
   string path=GameInstall.Safe(root,file.path,true,checkedDirectories);
   if(!File.Exists(path)||new FileInfo(path).Length!=file.size)return false;
  }
  return true;
 }
 internal static void Download(string partial,Action<int,string> progress){
  GameInstall.Gate.Wait();
  ServicePointManager.SecurityProtocol=SecurityProtocolType.Tls12;
  string initial="https://drive.usercontent.google.com/uc?id=1kPYd50_NDAniYPb2eRyLz1CSSIVd612i&export=download";
  string url=initial;var first=(HttpWebRequest)WebRequest.Create(initial);first.Timeout=20000;
  using(var response=(HttpWebResponse)first.GetResponse()){
   if(response.ContentType.StartsWith("text/html",StringComparison.OrdinalIgnoreCase)){
    using(var reader=new StreamReader(response.GetResponseStream())){char[] buffer=new char[1048576];int count=reader.ReadBlock(buffer,0,buffer.Length);string html=new string(buffer,0,count);
     var uuid=Regex.Match(html,"name=\"uuid\" value=\"([a-zA-Z0-9-]+)\"");
     if(!uuid.Success)throw new IOException("Google Drive не разрешил скачивание. Проверьте доступ к архиву или лимит скачиваний.");
     url="https://drive.usercontent.google.com/download?id=1kPYd50_NDAniYPb2eRyLz1CSSIVd612i&export=download&confirm=t&uuid="+uuid.Groups[1].Value;
    }
   }
  }
  for(int attempt=0;attempt<4;attempt++){
   GameInstall.Gate.Wait();long position=File.Exists(partial)?new FileInfo(partial).Length:0;if(position==ArchiveSize)return;if(position>ArchiveSize){File.Delete(partial);position=0;}
   var request=(HttpWebRequest)WebRequest.Create(url);request.Timeout=30000;request.ReadWriteTimeout=30000;if(position>0)request.AddRange(position);
   try{using(var response=(HttpWebResponse)request.GetResponse()){
    if(response.ContentType.StartsWith("text/html",StringComparison.OrdinalIgnoreCase))throw new IOException("Google Drive вернул страницу вместо архива. Возможно, исчерпан лимит скачиваний.");
    if(position>0&&response.StatusCode==HttpStatusCode.PartialContent){string range=response.Headers["Content-Range"];if(range==null||!range.StartsWith("bytes "+position+"-"))throw new IOException("Неверный диапазон загрузки");}
    else position=0;
    using(var input=response.GetResponseStream())using(var output=new FileStream(partial,position>0?FileMode.Append:FileMode.Create,FileAccess.Write,FileShare.Read,1048576)){
     byte[] buffer=new byte[1048576];int read;var clock=Stopwatch.StartNew();long start=position;
     while(true){GameInstall.Gate.Wait();read=input.Read(buffer,0,buffer.Length);if(read==0)break;position+=read;if(position>ArchiveSize)throw new IOException("Размер архива превышен");output.Write(buffer,0,read);double rate=(position-start)/Math.Max(1,clock.Elapsed.TotalSeconds)/1048576;progress((int)(position*100/ArchiveSize),"Загрузка · "+(position/1048576)+" / "+(ArchiveSize/1048576)+" МБ · "+rate.ToString("0.0")+" МБ/с");}
    }
    if(position!=ArchiveSize)throw new IOException("Загрузка прервалась до завершения");return;
   }}catch(IOException){if(attempt==3)throw;}
  }
 }
}
