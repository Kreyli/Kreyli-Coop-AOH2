using System;using System.IO;using System.Linq;using System.Collections.Generic;using System.Security.Cryptography;using System.Web.Script.Serialization;
class MakeGamePackage{
 static int Main(string[] args){try{return Run(args);}catch(Exception e){Console.Error.WriteLine(e);return 1;}} static int Run(string[] args){string root=Path.GetFullPath(args[0]),output=Path.GetFullPath(args[1]);var files=new List<object>();var paths=new List<string>{Path.Combine(root,"game.jar")};foreach(string folder in new[]{"game","map","music","sounds","UI","lwjgl"})paths.AddRange(Directory.GetFiles(Path.Combine(root,folder),"*",SearchOption.AllDirectories));
 Console.WriteLine("Files="+paths.Count); foreach(string path in paths){if(files.Count%10000==0)Console.WriteLine("Hashed="+files.Count);string hash;using(var sha=SHA256.Create())using(var stream=File.OpenRead(path))hash=BitConverter.ToString(sha.ComputeHash(stream)).Replace("-","").ToLowerInvariant();files.Add(new{path=path.Substring(root.TrimEnd('\\').Length+1).Replace('\\','/'),size=new FileInfo(path).Length,sha256=hash});}
 File.WriteAllText(output,new JavaScriptSerializer{MaxJsonLength=64*1024*1024}.Serialize(new{files=files}));Console.WriteLine("Manifest files="+files.Count);return 0;
 }
}
