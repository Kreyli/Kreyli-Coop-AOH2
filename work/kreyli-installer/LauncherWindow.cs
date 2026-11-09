using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Reflection;
using System.Threading.Tasks;
using System.Windows.Forms;

internal sealed class LauncherWindow:Form {
 readonly TextBox nickname=new TextBox();readonly Label status=new Label(),detail=new Label();
 readonly Button play=new KreyliButton{LeadingIcon="play"},menu=new KreyliButton{LeadingIcon="chevron"};readonly KreyliProgress progress=new KreyliProgress();
 readonly string[] gameArgs;readonly Timer autoTimer=new Timer(),revealTimer=new Timer(),pollTimer=new Timer();readonly ToolTip hints=new ToolTip();
 ContextMenuStrip actions;Image backdrop,logo;bool busy,gameRunning,transfer,closingForUpdate,checkingUpdates;UpdateManifest pending;DateTime lastProgress=DateTime.MinValue;
 static readonly Color Red=Color.FromArgb(202,29,57),Blue=Color.FromArgb(39,110,209),Muted=Color.FromArgb(156,164,179);
 public LauncherWindow(string[] args){
  gameArgs=args;Text="Kreyli Coop AOH2 · 1.1.6";ClientSize=new Size(1200,700);FormBorderStyle=FormBorderStyle.None;MaximizeBox=false;StartPosition=FormStartPosition.CenterScreen;BackColor=Color.FromArgb(11,13,18);ForeColor=Color.White;Font=new Font("Segoe UI",11);DoubleBuffered=true;AutoScaleMode=AutoScaleMode.None;
  backdrop=Asset("background.png");logo=Asset("logo.png");string ico=Path.Combine(Launcher.Root,"kreyli.ico");if(File.Exists(ico))Icon=new Icon(ico);
  var minimize=new KreyliButton{Quiet=true};ButtonAt(minimize,"—",1080,19,44,36,Color.FromArgb(20,23,30));minimize.Click+=(s1,e)=>WindowState=FormWindowState.Minimized;hints.SetToolTip(minimize,"Свернуть");
  var close=new KreyliButton{Quiet=true};ButtonAt(close,"×",1134,19,40,36,Color.FromArgb(28,22,29));close.Click+=(s1,e)=>Close();hints.SetToolTip(close,"Закрыть");
  MouseDown+=(s1,e)=>{if(e.Button==MouseButtons.Left&&e.Y<78){ReleaseCapture();SendMessage(Handle,0xA1,2,0);}};
  LabelAt("AGE OF HISTORY II",48,340,650,48,28,FontStyle.Bold,Color.FromArgb(245,242,239));
  LabelAt("DEFINITIVE EDITION  /  CO-OP",50,392,590,25,11,FontStyle.Regular,Color.FromArgb(208,170,145));
  var console=new KreyliCard{Fill=Color.FromArgb(19,22,29),Line=Color.FromArgb(49,54,66),Radius=18};console.SetBounds(32,531,1136,124);Controls.Add(console);
  AddLabel(console,"ВАШ НИКНЕЙМ",23,16,290,20,8,FontStyle.Bold,Muted);
  var input=new KreyliCard{Fill=Color.FromArgb(12,15,21),Line=Color.FromArgb(56,62,78),Radius=10};input.SetBounds(23,43,339,49);console.Controls.Add(input);
  nickname.SetBounds(16,13,307,28);nickname.Font=new Font("Segoe UI",13);nickname.BackColor=input.Fill;nickname.ForeColor=Color.FromArgb(238,240,247);nickname.BorderStyle=BorderStyle.None;nickname.MaxLength=24;input.Controls.Add(nickname);
  nickname.Enter+=(s1,e)=>{input.Line=Color.FromArgb(188,74,94);input.Invalidate();};nickname.Leave+=(s1,e)=>{input.Line=Color.FromArgb(56,62,78);input.Invalidate();};hints.SetToolTip(nickname,"Этот ник будет использоваться во всех лобби");
  try{string profile=Path.Combine(Launcher.Root,"launcher-profile.json");if(File.Exists(profile))nickname.Text=Launcher.Json.Deserialize<Profile>(File.ReadAllText(profile)).nickname;}catch{}
  ButtonAt(play,"Играть",425,561,326,62,Red);play.Font=new Font("Segoe UI Semibold",20,FontStyle.Bold);play.Click+=async(s1,e)=>await Primary();play.BringToFront();
  ButtonAt(menu,"",761,561,56,62,Color.FromArgb(38,42,53));menu.AccessibleName="Действия с игрой";hints.SetToolTip(menu,"Обновления, проверка файлов и удаление");
  actions=new ContextMenuStrip{BackColor=Color.FromArgb(24,27,35),ForeColor=Color.FromArgb(229,233,241),ShowImageMargin=false,Font=new Font("Segoe UI",10),Padding=new Padding(6),Renderer=new KreyliMenuRenderer()};
  actions.Items.Add("Проверить обновления",null,async(s1,e)=>await CheckUpdates(false));
  actions.Items.Add("Проверить целостность файлов",null,async(s1,e)=>await FileOperation(()=>GameInstall.Verify(Progress),false));
  actions.Items.Add(new ToolStripSeparator());actions.Items.Add("Удалить игру",null,async(s1,e)=>await RemoveGame());
  foreach(ToolStripItem item in actions.Items)item.Padding=new Padding(10,7,10,7);
  menu.Click+=(s1,e)=>actions.Show(menu,new Point(0,-actions.PreferredSize.Height-8));
  var discord=new KreyliButton{LeadingIcon="discord"};ButtonAt(discord,"Discord",847,561,286,62,Color.FromArgb(41,45,73));discord.Click+=(s1,e)=>{try{System.Diagnostics.Process.Start("https://discord.gg/zGdxgSvW4");}catch(Exception ex){Status(ex.Message);}};hints.SetToolTip(discord,"Открыть сообщество Kreyli");
  progress.SetBounds(425,635,708,4);progress.Visible=false;Controls.Add(progress);progress.BringToFront();
  detail.SetBounds(56,634,335,18);detail.ForeColor=Color.FromArgb(117,128,147);detail.BackColor=console.Fill;detail.Font=new Font("Segoe UI",7.5f,FontStyle.Bold);Controls.Add(detail);detail.BringToFront();
  status.SetBounds(43,668,1109,23);status.Font=new Font("Segoe UI",9);status.ForeColor=Muted;status.BackColor=Color.Transparent;status.AutoEllipsis=true;Controls.Add(status);hints.SetToolTip(status,"Состояние игры и обновлений");
  autoTimer.Interval=1500;autoTimer.Tick+=async(s1,e)=>{autoTimer.Stop();if(pending!=null&&!busy&&!gameRunning)await UpdateFiles();};
  pollTimer.Interval=15000;pollTimer.Tick+=async(s1,e)=>await CheckUpdates(true);
  revealTimer.Interval=16;revealTimer.Tick+=(s1,e)=>{Opacity=Math.Min(1,Opacity+0.10);if(Opacity>=1)revealTimer.Stop();};
  Shown+=async(s1,e)=>{if(SystemInformation.IsMenuAnimationEnabled){Opacity=0.15;revealTimer.Start();}RefreshState();pollTimer.Start();await CheckUpdates(true);};
  FormClosing+=(s1,e)=>{if((busy||gameRunning)&&!closingForUpdate){e.Cancel=true;Status(gameRunning?"Сначала завершите игру.":"Дождитесь окончания операции; загрузку можно поставить на паузу.");}};
  foreach(Control control in new Control[]{play,menu,discord,progress,detail}){int x=control.Left-console.Left,y=control.Top-console.Top;console.Controls.Add(control);control.Location=new Point(x,y);control.BringToFront();}
  RefreshState();
 }
 static Image Asset(string name){using(var stream=Assembly.GetExecutingAssembly().GetManifestResourceStream(name)){if(stream==null)return null;using(var image=Image.FromStream(stream))return new Bitmap(image);}}
 [System.Runtime.InteropServices.DllImport("user32.dll")]static extern bool ReleaseCapture();
 [System.Runtime.InteropServices.DllImport("user32.dll")]static extern IntPtr SendMessage(IntPtr h,int m,int w,int l);
 protected override void Dispose(bool disposing){if(disposing){autoTimer.Dispose();revealTimer.Dispose();pollTimer.Dispose();hints.Dispose();if(actions!=null)actions.Dispose();if(backdrop!=null)backdrop.Dispose();if(logo!=null)logo.Dispose();if(Icon!=null)Icon.Dispose();}base.Dispose(disposing);}
 protected override void OnPaint(PaintEventArgs e){
  base.OnPaint(e);var g=e.Graphics;g.SmoothingMode=SmoothingMode.AntiAlias;
  if(backdrop!=null)g.DrawImage(backdrop,new Rectangle(0,78,1200,490));
  using(var shade=new LinearGradientBrush(new Rectangle(0,78,1200,490),Color.FromArgb(90,9,12,18),Color.FromArgb(225,9,12,18),LinearGradientMode.Vertical))g.FillRectangle(shade,0,78,1200,490);
  using(var left=new LinearGradientBrush(new Rectangle(0,78,780,490),Color.FromArgb(150,9,12,18),Color.FromArgb(5,9,12,18),LinearGradientMode.Horizontal))g.FillRectangle(left,0,78,780,490);
  if(logo!=null){var box=new Rectangle(48,90,390,260);g.DrawImage(logo,box);using(var edge=new LinearGradientBrush(box,Color.FromArgb(0,11,13,18),Color.FromArgb(255,11,13,18),LinearGradientMode.Vertical)){var blend=new ColorBlend{Colors=new[]{Color.FromArgb(0,11,13,18),Color.FromArgb(0,11,13,18),Color.FromArgb(255,11,13,18)},Positions=new[]{0f,0.78f,1f}};edge.InterpolationColors=blend;g.FillRectangle(edge,box);}}
  using(var line=new Pen(Color.FromArgb(40,44,55))){g.DrawLine(line,32,78,1168,78);g.DrawRectangle(line,0,0,Width-1,Height-1);}
 }
 static Label AddLabel(Control parent,string text,int x,int y,int w,int h,float size,FontStyle style,Color color){var label=new Label{Text=text,Font=new Font("Segoe UI",size,style),ForeColor=color,BackColor=Color.Transparent,AutoEllipsis=true};label.SetBounds(x,y,w,h);parent.Controls.Add(label);return label;}
 void LabelAt(string text,int x,int y,int w,int h,float size,FontStyle style,Color color){AddLabel(this,text,x,y,w,h,size,style,color);}
 void ButtonAt(Button b,string text,int x,int y,int w,int h,Color color){b.Text=text;b.SetBounds(x,y,w,h);b.BackColor=color;b.ForeColor=Color.White;b.Font=new Font("Segoe UI Semibold",12,FontStyle.Bold);b.Cursor=Cursors.Hand;Controls.Add(b);b.BringToFront();}
 void Status(string text){if(IsDisposed)return;if(InvokeRequired){BeginInvoke(new Action<string>(Status),text);return;}status.Text=text;}
 void Progress(int percent,string text){if((DateTime.UtcNow-lastProgress).TotalMilliseconds<100&&percent>0&&percent<100)return;lastProgress=DateTime.UtcNow;if(IsDisposed)return;BeginInvoke(new Action(()=>{progress.Value=Math.Max(0,Math.Min(100,percent));if(!GameInstall.Paused)Status(text);}));}
 void RefreshState(){bool installed=false;try{installed=GameInstall.Installed;}catch(Exception ex){Status(ex.Message);}play.Text=gameRunning?"Игра запущена":transfer?(GameInstall.Paused?"Продолжить":"Пауза"):(busy?"Проверка…":(pending!=null?"Обновить":!installed?"Установить":"Играть"));play.BackColor=installed&&pending==null&&!transfer?Red:Blue;play.Enabled=transfer||(!busy&&!gameRunning);menu.Enabled=nickname.Enabled=!busy&&!gameRunning;detail.Text=gameRunning?"СЕССИЯ АКТИВНА":transfer?"ПОДГОТОВКА ИГРЫ":installed?"ГОТОВО К ЗАПУСКУ":"ТРЕБУЕТСЯ УСТАНОВКА";progress.Visible=transfer;}
 async Task Primary(){if(transfer){GameInstall.Pause(!GameInstall.Paused);Status(GameInstall.Paused?"Загрузка приостановлена":"Продолжаем…");RefreshState();return;}if(busy||gameRunning)return;
  if(pending!=null){await UpdateFiles();return;}
  if(!GameInstall.Installed){using(var dialog=new FolderBrowserDialog{Description="Выберите папку. Игра будет установлена в подпапку Kreyli Coop AOH2 Game.",ShowNewFolderButton=true}){if(dialog.ShowDialog(this)!=DialogResult.OK)return;string destination=Path.Combine(dialog.SelectedPath,"Kreyli Coop AOH2 Game");await FileOperation(()=>GameInstall.Install(destination,Progress),true);}return;}
  await Launch();
 }
 async Task CheckUpdates(bool background){
  if(busy||checkingUpdates||pending!=null)return;
  checkingUpdates=true;
  try{
   var available=await Task.Run(()=>Launcher.CheckUpdate());
   if(IsDisposed||closingForUpdate)return;
   if(available!=null){
    pending=available;
    Status(gameRunning?"Обновление установится после выхода из игры":"Доступно обновление "+pending.version);
    if(!gameRunning&&!busy)autoTimer.Start();
   }else if(!background&&!gameRunning){
    Status(Launcher.Configured()?"Установлена актуальная версия":"Канал обновлений пока не подключён");
   }
  }catch(Exception ex){
   if(!background&&!IsDisposed&&!gameRunning)Status("Проверка обновлений недоступна: "+ex.Message);
  }finally{
   checkingUpdates=false;
   if(!IsDisposed)RefreshState();
  }
 }
 async Task UpdateFiles(){if(pending==null||busy||gameRunning)return;autoTimer.Stop();busy=transfer=true;GameInstall.Pause(false);RefreshState();try{Launcher.TransferProgress=Progress;Status("Загрузка обновления…");string stage=await Task.Run(()=>Launcher.Stage(pending,Status));Status("Применение обновления…");transfer=false;RefreshState();Launcher.StartUpdater(stage);closingForUpdate=true;Close();}catch(Exception ex){Status("Обновление не установлено: "+ex.Message);}finally{Launcher.TransferProgress=null;GameInstall.Pause(false);busy=transfer=false;if(!IsDisposed)RefreshState();}}
 async Task FileOperation(Action action,bool pausable){if(busy||gameRunning)return;autoTimer.Stop();busy=true;transfer=pausable;GameInstall.Pause(false);RefreshState();try{await Task.Run(action);}catch(Exception ex){try{File.WriteAllText(Path.Combine(Launcher.Root,"launcher-error.txt"),ex.ToString());}catch{}Status(ex.Message);}finally{GameInstall.Pause(false);busy=transfer=false;RefreshState();if(pending!=null)autoTimer.Start();}}
 async Task RemoveGame(){if(!GameInstall.Installed)return;if(MessageBox.Show(this,"Удалить файлы игры? Лаунчер, сохранения и настройки останутся.","Удаление игры",MessageBoxButtons.YesNo,MessageBoxIcon.Question)!=DialogResult.Yes)return;await FileOperation(()=>GameInstall.Remove(Progress),false);}
 async Task Launch(){if(pending!=null){await UpdateFiles();return;}string nick;try{nick=Launcher.Nick(nickname.Text);File.WriteAllText(Path.Combine(Launcher.Root,"launcher-profile.json"),Launcher.Json.Serialize(new Profile{nickname=nick}));}catch(Exception ex){Status(ex.Message);nickname.Focus();return;}autoTimer.Stop();gameRunning=true;RefreshState();Status("Игра запущена");try{await Task.Run(()=>Launcher.RunGame(nick,gameArgs));Status("Игра завершена");}catch(Exception ex){Status(ex.Message);}finally{gameRunning=false;RefreshState();if(pending!=null)autoTimer.Start();}}
}

internal static class KreyliDrawing {
 internal static GraphicsPath Round(RectangleF bounds,float radius){var path=new GraphicsPath();float d=radius*2;path.AddArc(bounds.X,bounds.Y,d,d,180,90);path.AddArc(bounds.Right-d,bounds.Y,d,d,270,90);path.AddArc(bounds.Right-d,bounds.Bottom-d,d,d,0,90);path.AddArc(bounds.X,bounds.Bottom-d,d,d,90,90);path.CloseFigure();return path;}
 internal static Color Mix(Color a,Color b,float t){return Color.FromArgb((int)(a.R+(b.R-a.R)*t),(int)(a.G+(b.G-a.G)*t),(int)(a.B+(b.B-a.B)*t));}
}
internal sealed class KreyliCard:Panel {
 internal Color Fill=Color.FromArgb(20,23,30),Line=Color.FromArgb(48,52,64);internal int Radius=16;
 internal KreyliCard(){SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.SupportsTransparentBackColor,true);BackColor=Color.Transparent;}
 protected override void OnPaint(PaintEventArgs e){var g=e.Graphics;g.SmoothingMode=SmoothingMode.AntiAlias;using(var path=KreyliDrawing.Round(new RectangleF(0.5f,0.5f,Width-1,Height-1),Radius))using(var fill=new SolidBrush(Fill))using(var line=new Pen(Line)){g.FillPath(fill,path);g.DrawPath(line,path);}base.OnPaint(e);}
}
internal sealed class KreyliButton:Button {
 internal string LeadingIcon="";internal bool Quiet;readonly Timer animation=new Timer();float hover;bool inside,pressed;
 internal KreyliButton(){SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.SupportsTransparentBackColor,true);FlatStyle=FlatStyle.Flat;FlatAppearance.BorderSize=0;UseVisualStyleBackColor=false;animation.Interval=16;animation.Tick+=(s,e)=>{float target=inside&&Enabled?1:0;hover+=(target-hover)*0.3f;if(Math.Abs(target-hover)<0.015f){hover=target;animation.Stop();}Invalidate();};}
 void Animate(){if(SystemInformation.IsMenuAnimationEnabled)animation.Start();else{hover=inside&&Enabled?1:0;Invalidate();}}
 protected override void OnMouseEnter(EventArgs e){base.OnMouseEnter(e);inside=true;Animate();}
 protected override void OnMouseLeave(EventArgs e){base.OnMouseLeave(e);inside=false;pressed=false;Animate();}
 protected override void OnMouseDown(MouseEventArgs e){base.OnMouseDown(e);if(e.Button==MouseButtons.Left){pressed=true;Invalidate();}}
 protected override void OnMouseUp(MouseEventArgs e){base.OnMouseUp(e);pressed=false;Invalidate();}
 protected override void OnEnabledChanged(EventArgs e){base.OnEnabledChanged(e);inside=false;pressed=false;Animate();}
 protected override void OnVisibleChanged(EventArgs e){base.OnVisibleChanged(e);if(!Visible){animation.Stop();hover=0;inside=false;pressed=false;}}
 protected override void OnGotFocus(EventArgs e){base.OnGotFocus(e);Invalidate();}
 protected override void OnLostFocus(EventArgs e){base.OnLostFocus(e);Invalidate();}
 protected override void OnPaintBackground(PaintEventArgs e){}
 protected override void OnPaint(PaintEventArgs e){
  var g=e.Graphics;
  var card=Parent as KreyliCard;
  g.Clear(card!=null?card.Fill:(Parent!=null?Parent.BackColor:SystemColors.Control));
  g.SmoothingMode=SmoothingMode.AntiAlias;
  var r=new RectangleF(0.5f,0.5f,Width-1f,Height-1f);Color basis=Enabled?BackColor:Color.FromArgb(34,38,48);Color top=KreyliDrawing.Mix(basis,Color.White,0.08f+hover*0.12f);Color bottom=KreyliDrawing.Mix(basis,Color.Black,pressed?0.23f:0.08f);if(Quiet){top=KreyliDrawing.Mix(basis,Color.White,hover*0.10f);bottom=top;}
  using(var shape=KreyliDrawing.Round(r,Quiet?7:11))using(var fill=new LinearGradientBrush(r,top,bottom,LinearGradientMode.Vertical))using(var edge=new Pen(KreyliDrawing.Mix(basis,Color.White,0.15f+hover*0.20f))){g.FillPath(fill,shape);g.DrawPath(edge,shape);}
  Color ink=Enabled?ForeColor:Color.FromArgb(126,134,151);int icon=LeadingIcon==""?0:22;var textRect=new Rectangle(12+icon,0,Width-24-icon,Height-2);if(Text.Length==0)textRect=new Rectangle(0,0,Width,Height);
  if(Quiet)textRect=new Rectangle(0,0,Width,Height);
  TextRenderer.DrawText(g,Text,Font,textRect,ink,TextFormatFlags.HorizontalCenter|TextFormatFlags.VerticalCenter|TextFormatFlags.SingleLine|TextFormatFlags.EndEllipsis);
  if(LeadingIcon!=""){float x=Text.Length==0?Width/2f:Math.Max(24,(Width-TextRenderer.MeasureText(Text,Font).Width-icon-14)/2f),y=Height/2f+(pressed?1:0);using(var pen=new Pen(ink,1.8f)){pen.StartCap=pen.EndCap=LineCap.Round;if(LeadingIcon=="chevron"){g.DrawLines(pen,new[]{new PointF(x-5,y-2),new PointF(x,y+3),new PointF(x+5,y-2)});}else if(LeadingIcon=="discord"){DrawDiscord(g,x+4,y,ink);}else if(Text=="Играть"){using(var brush=new SolidBrush(ink))g.FillPolygon(brush,new[]{new PointF(x-4,y-7),new PointF(x-4,y+7),new PointF(x+6,y)});}else if(Text=="Пауза"){g.DrawLine(pen,x-3,y-6,x-3,y+6);g.DrawLine(pen,x+3,y-6,x+3,y+6);}else{g.DrawLine(pen,x,y-6,x,y+5);g.DrawLines(pen,new[]{new PointF(x-4,y+1),new PointF(x,y+5),new PointF(x+4,y+1)});}}}
  if(Focused&&ShowFocusCues)using(var focus=new Pen(Color.FromArgb(185,215,226,251))){focus.DashStyle=DashStyle.Dot;using(var path=KreyliDrawing.Round(new RectangleF(5,5,Width-11,Height-12),8))g.DrawPath(focus,path);}
 }
 static void DrawDiscord(Graphics g,float x,float y,Color color){
  var state=g.Save();
  try{
   g.TranslateTransform(x-12,y-10);
   using(var path=new GraphicsPath(FillMode.Alternate))
   using(var brush=new SolidBrush(color)){
    path.AddBeziers(new[]{
     new PointF(4,3),new PointF(5,2),new PointF(7,1.5f),new PointF(9,1),
     new PointF(9.4f,1.6f),new PointF(9.7f,2.2f),new PointF(9.9f,2.7f),
     new PointF(11.2f,2.4f),new PointF(12.8f,2.4f),new PointF(14.1f,2.7f),
     new PointF(14.3f,2.2f),new PointF(14.6f,1.6f),new PointF(15,1),
     new PointF(17,1.5f),new PointF(19,2),new PointF(20,3),
     new PointF(22.5f,6.7f),new PointF(24,10.7f),new PointF(23.5f,15.2f),
     new PointF(21.5f,16.8f),new PointF(19.8f,17.6f),new PointF(17.5f,18.2f),
     new PointF(16.8f,17.4f),new PointF(16.5f,16.8f),new PointF(16.2f,16.1f),
     new PointF(17.2f,15.7f),new PointF(18,15.3f),new PointF(18.4f,14.8f),
     new PointF(14.3f,16.7f),new PointF(9.7f,16.7f),new PointF(5.6f,14.8f),
     new PointF(6,15.3f),new PointF(6.8f,15.7f),new PointF(7.8f,16.1f),
     new PointF(7.5f,16.8f),new PointF(7.2f,17.4f),new PointF(6.5f,18.2f),
     new PointF(4.2f,17.6f),new PointF(2.5f,16.8f),new PointF(0.5f,15.2f),
     new PointF(0,10.7f),new PointF(1.5f,6.7f),new PointF(4,3)
    });
    path.CloseFigure();
    path.AddEllipse(6.4f,8.4f,3.8f,4.6f);
    path.AddEllipse(13.8f,8.4f,3.8f,4.6f);
    g.FillPath(brush,path);
   }
  }finally{g.Restore(state);}
 }
 protected override void Dispose(bool disposing){if(disposing)animation.Dispose();base.Dispose(disposing);}
}
internal sealed class KreyliProgress:Control {
 readonly Timer animation=new Timer();int value;float displayed;
 internal int Value{get{return value;}set{this.value=Math.Max(0,Math.Min(100,value));if(Visible&&SystemInformation.IsMenuAnimationEnabled)animation.Start();else{displayed=this.value;Invalidate();}}}
 internal KreyliProgress(){SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer,true);animation.Interval=16;animation.Tick+=(s,e)=>{displayed+=(value-displayed)*0.18f;if(Math.Abs(value-displayed)<0.15f){displayed=value;animation.Stop();}Invalidate();};}
 protected override void OnVisibleChanged(EventArgs e){base.OnVisibleChanged(e);if(!Visible)animation.Stop();else{displayed=value;Invalidate();}}
 protected override void OnPaint(PaintEventArgs e){e.Graphics.Clear(Color.FromArgb(38,43,55));if(displayed>0)using(var brush=new LinearGradientBrush(ClientRectangle,Color.FromArgb(38,99,192),Color.FromArgb(105,175,252),LinearGradientMode.Horizontal))e.Graphics.FillRectangle(brush,0,0,Width*displayed/100f,Height);}
 protected override void Dispose(bool disposing){if(disposing)animation.Dispose();base.Dispose(disposing);}
}
internal sealed class KreyliMenuRenderer:ToolStripProfessionalRenderer {
 protected override void OnRenderMenuItemBackground(ToolStripItemRenderEventArgs e){if(e.Item.Selected){e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;using(var p=KreyliDrawing.Round(new RectangleF(2,1,e.Item.Width-4,e.Item.Height-2),6))using(var b=new SolidBrush(Color.FromArgb(55,43,56)))e.Graphics.FillPath(b,p);}}
 protected override void OnRenderToolStripBorder(ToolStripRenderEventArgs e){using(var pen=new Pen(Color.FromArgb(64,68,82)))e.Graphics.DrawRectangle(pen,0,0,e.ToolStrip.Width-1,e.ToolStrip.Height-1);}
}


