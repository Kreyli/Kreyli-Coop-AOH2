using System; using System.IO; class Stub { static void Main(){File.WriteAllText(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"new-started.txt"),"replacement restarted");} }
