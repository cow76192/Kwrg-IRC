using System;
using System.Collections.Generic;
using System.Threading;

public static partial class CheatProgramWindowCapture {
 static volatile IntPtr[] OwnedWindows=new IntPtr[0];
 static OwnedComposition Composition;
 static bool IsOwnedWindow(IntPtr window){
  if(window==IntPtr.Zero||window==Selected||SelectedMonitor)return false;
  for(int depth=0;depth<16&&window!=IntPtr.Zero;depth++){window=GetWindow(window,4);if(window==Selected)return true;}
  return false;
 }
 static bool Contains(RECT bounds,POINT point){return point.X>=bounds.L&&point.X<bounds.R&&point.Y>=bounds.T&&point.Y<bounds.B;}
 static IntPtr RootAtPoint(POINT point){
  foreach(IntPtr window in OwnedWindows){RECT bounds;if(IsOwnedWindow(window)&&IsWindowVisible(window)&&IsWindowEnabled(window)&&GetWindowRect(window,out bounds)&&Contains(bounds,point))return window;}
  POINT local=point;RECT client;if(ScreenToClient(Selected,ref local)&&GetClientRect(Selected,out client)&&Contains(client,local)&&IsWindowEnabled(Selected))return Selected;
  return IntPtr.Zero;
 }
 sealed class OwnedSource {
  public readonly IntPtr Window;public ReadyFrame Frame;public RECT Bounds;public volatile bool Closed;public Wgc Capture;public Thread Worker;
  public OwnedSource(IntPtr window){Window=window;}
  public void Stop(){Closed=true;Wgc current=Capture;if(current!=null)current.Stop();}
 }
 sealed class OwnedComposition:IDisposable {
  readonly object sync=new object();readonly int edge,pixels,fps;readonly bool native;readonly long owner;
  readonly List<OwnedSource> sources=new List<OwnedSource>();ReadyFrame parent;long nextSearch;bool disposed;POINT rootPosition;
  struct Layer {public ReadyFrame Frame;public int X,Y;}
  readonly IntPtr marker=new IntPtr(System.Diagnostics.Process.GetCurrentProcess().Id);readonly IntPtr previousMarker=GetPropW(Selected,"kwrg_projection_owned_input");
  public OwnedComposition(int edge,int pixels,int fps,bool native,long owner){this.edge=edge;this.pixels=pixels;this.fps=fps;this.native=native;this.owner=owner;}
  public void Refresh(){
   long now=DateTime.UtcNow.Ticks;if(now<nextSearch)return;nextSearch=now+100*TimeSpan.TicksPerMillisecond;
   lock(InputLock){var closed=new List<IntPtr>();foreach(var entry in Views)if(entry.Key!=Selected&&!ValidView(entry.Value.Identity))closed.Add(entry.Key);foreach(IntPtr window in closed)Views.Remove(window);}
   var windows=new List<IntPtr>();
   EnumWindows(delegate(IntPtr window,IntPtr ignored){uint affinity;RECT rect;
    if(windows.Count>=16)return false;
    if(!IsOwnedWindow(window)||!IsWindowVisible(window)||IsIconic(window)||Cloaked(window)||ImeWindow(window)||GetWindowDisplayAffinity(window,out affinity)&&affinity!=0)return true;
    if(!GetWindowRect(window,out rect)||rect.R-rect.L<2||rect.B-rect.T<2||rect.R-rect.L>MaxEdge||rect.B-rect.T>MaxEdge)return true;
    lock(InputLock){if(Remember(window)==null)return true;}windows.Add(window);return true;
   },IntPtr.Zero);
   IntPtr[] previous=OwnedWindows;bool reordered=previous.Length!=windows.Count;for(int i=0;!reordered&&i<previous.Length;i++)if(previous[i]!=windows[i])reordered=true;
   OwnedWindows=windows.ToArray();
   lock(InputLock){
    if(windows.Count!=0)SetPropW(Selected,"kwrg_projection_owned_input",marker);else if(GetPropW(Selected,"kwrg_projection_owned_input")==marker){if(previousMarker==IntPtr.Zero)RemovePropW(Selected,"kwrg_projection_owned_input");else SetPropW(Selected,"kwrg_projection_owned_input",previousMarker);}
   }
   lock(sync){
    if(disposed)return;bool changed=reordered;POINT position=new POINT(0,0);if(ClientToScreen(Selected,ref position)&&(position.X!=rootPosition.X||position.Y!=rootPosition.Y)){rootPosition=position;changed=true;}
    for(int i=sources.Count-1;i>=0;i--)if(!windows.Contains(sources[i].Window)){OwnedSource source=sources[i];source.Stop();if(source.Frame!=null)ReturnPixels(source.Frame.Pixels);sources.RemoveAt(i);changed=true;}
    foreach(IntPtr window in windows){bool exists=false;foreach(OwnedSource source in sources)if(source.Window==window){exists=true;break;}if(exists)continue;
     var added=new OwnedSource(window);sources.Add(added);changed=true;
     added.Worker=new Thread(delegate(){Dpi();try{using(var capture=new Wgc(window,true)){added.Capture=capture;if(added.Closed)capture.Stop();capture.Run(edge,pixels,fps,native,owner,delegate(ReadyFrame frame){Child(added,frame);});}}catch(Exception failure){if(!added.Closed&&!Quitting)Console.Error.WriteLine("Owned capture: "+failure.GetType().Name);}finally{added.Capture=null;}});
     added.Worker.IsBackground=true;added.Worker.Name="projection-owned-window";added.Worker.Start();
    }
    sources.Sort(delegate(OwnedSource a,OwnedSource b){return windows.IndexOf(a.Window).CompareTo(windows.IndexOf(b.Window));});
    foreach(OwnedSource source in sources){RECT bounds;if(GetWindowRect(source.Window,out bounds)&&(bounds.L!=source.Bounds.L||bounds.T!=source.Bounds.T||bounds.R!=source.Bounds.R||bounds.B!=source.Bounds.B)){source.Bounds=bounds;changed=true;}}
    if(changed&&parent!=null)Publish();
   }
  }
  public void Parent(ReadyFrame frame){lock(sync){if(disposed){ReturnPixels(frame.Pixels);return;}if(parent!=null)ReturnPixels(parent.Pixels);parent=frame;Publish();}}
  void Child(OwnedSource source,ReadyFrame frame){lock(sync){if(disposed||source.Closed){ReturnPixels(frame.Pixels);return;}if(source.Frame!=null)ReturnPixels(source.Frame.Pixels);source.Frame=frame;if(parent!=null)Publish();}}
  void Publish(){
   POINT position=new POINT(0,0);if(!ClientToScreen(Selected,ref position))return;int rootX=position.X,rootY=position.Y;
   int left=parent.OriginX,top=parent.OriginY,right=left+parent.CW,bottom=top+parent.CH;
   var included=new List<Layer>();long captured=parent.Captured;
   foreach(OwnedSource source in sources){ReadyFrame frame=source.Frame;if(frame==null||source.Closed)continue;
    RECT bounds;if(!GetWindowRect(source.Window,out bounds))continue;
    int x=bounds.L+frame.ScreenX-frame.WindowX-rootX,y=bounds.T+frame.ScreenY-frame.WindowY-rootY,l=Math.Min(left,x),t=Math.Min(top,y),r=Math.Max(right,x+frame.CW),b=Math.Max(bottom,y+frame.CH);
    if(l<-MaxEdge||t<-MaxEdge||r>MaxEdge||b>MaxEdge||r-l>MaxEdge||b-t>MaxEdge||(long)(r-l)*(b-t)>MaxPixels)continue;
    left=l;top=t;right=r;bottom=b;included.Add(new Layer{Frame=frame,X=x,Y=y});captured=Math.Max(captured,frame.Captured);
   }
   if(included.Count==0){byte[] copy=RentPixels(parent.Pixels.Length);Buffer.BlockCopy(parent.Pixels,0,copy,0,copy.Length);Packet(1,parent.W,parent.H,parent.CW,parent.CH,copy,parent.Captured,parent.OriginX,parent.OriginY);return;}
   int cw=right-left,ch=bottom-top,w,h;Dimensions(cw,ch,edge,pixels,native,out w,out h);
   byte[] result=RentPixels(checked(w*h*4));Array.Clear(result,0,result.Length);
   DrawLayer(result,w,h,parent,(int)Math.Round((parent.OriginX-left)*(double)w/cw),(int)Math.Round((parent.OriginY-top)*(double)h/ch),Math.Max(1,(int)Math.Round(parent.CW*(double)w/cw)),Math.Max(1,(int)Math.Round(parent.CH*(double)h/ch)),false);
   for(int i=included.Count-1;i>=0;i--){Layer layer=included[i];ReadyFrame frame=layer.Frame;DrawLayer(result,w,h,frame,(int)Math.Round((layer.X-left)*(double)w/cw),(int)Math.Round((layer.Y-top)*(double)h/ch),Math.Max(1,(int)Math.Round(frame.CW*(double)w/cw)),Math.Max(1,(int)Math.Round(frame.CH*(double)h/ch)),true);}
   for(int p=0;p<result.Length;p+=4){int a=result[p+3];if(a!=0&&a!=255)for(int c=0;c<3;c++)result[p+c]=(byte)Math.Min(255,(result[p+c]*255+a/2)/a);}
   Packet(1,w,h,cw,ch,result,captured,left,top,true);
  }
  public void Dispose(){lock(sync){disposed=true;foreach(OwnedSource source in sources){source.Stop();if(source.Frame!=null)ReturnPixels(source.Frame.Pixels);}sources.Clear();if(parent!=null)ReturnPixels(parent.Pixels);parent=null;OwnedWindows=new IntPtr[0];
   if(GetPropW(Selected,"kwrg_projection_owned_input")==marker){if(previousMarker==IntPtr.Zero)RemovePropW(Selected,"kwrg_projection_owned_input");else SetPropW(Selected,"kwrg_projection_owned_input",previousMarker);}
  }}
 }
 static void DrawLayer(byte[] output,int width,int height,ReadyFrame frame,int left,int top,int w,int h,bool alpha){
  byte[] source=frame.W==w&&frame.H==h?frame.Pixels:Resize(frame.Pixels,frame.W,frame.H,w,h);
  try{int x0=Math.Max(0,left),y0=Math.Max(0,top),x1=Math.Min(width,left+w),y1=Math.Min(height,top+h);
   for(int y=y0;y<y1;y++){if(!alpha){if(x1>x0)Buffer.BlockCopy(source,((y-top)*w+x0-left)*4,output,(y*width+x0)*4,(x1-x0)*4);continue;}
    for(int x=x0;x<x1;x++){int p=((y-top)*w+x-left)*4,q=(y*width+x)*4,a=source[p+3];
    if(a==0)continue;
    for(int c=0;c<3;c++)output[q+c]=a==255?source[p+c]:(byte)Math.Min(255,source[p+c]+(output[q+c]*(255-a)+127)/255);
    output[q+3]=(byte)(a+(output[q+3]*(255-a)+127)/255);
   }}
  }finally{if(source!=frame.Pixels)ReturnPixels(source);}
 }
}
