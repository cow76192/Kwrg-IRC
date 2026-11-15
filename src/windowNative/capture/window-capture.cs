// Built with the Windows .NET Framework compiler; no NuGet or separately installed runtime is required.
// Windows.Graphics.Capture is projected from the operating system WinMetadata contract.
using System;
using System.IO;
using System.Text;
using System.Threading;
using System.Diagnostics;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Graphics;
using Windows.Graphics.Capture;
using Windows.Graphics.DirectX;
using Windows.Graphics.DirectX.Direct3D11;

public static partial class CheatProgramWindowCapture {
 const int MaxEdge=8192,MaxPixels=33554432;
 [StructLayout(LayoutKind.Sequential)] struct RECT { public int L,T,R,B; }
 [StructLayout(LayoutKind.Sequential)] struct POINT { public int X,Y; public POINT(int x,int y){X=x;Y=y;} }
 [StructLayout(LayoutKind.Sequential)] struct CURSORINFO {public int Size,Flags;public IntPtr Cursor;public POINT Position;}
 [StructLayout(LayoutKind.Sequential)] struct MESSAGE {public IntPtr Window;public uint Id;public UIntPtr W;public IntPtr L;public uint Time;public POINT Position;public uint Private;}
 delegate IntPtr MouseProcedure(int code,IntPtr message,IntPtr data);
 [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] struct MONITORINFOEX { public int Size;public RECT Monitor,Work;public uint Flags;[MarshalAs(UnmanagedType.ByValTStr,SizeConst=32)]public string Device; }
 [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] struct DISPLAYDEVICE { public int Size;[MarshalAs(UnmanagedType.ByValTStr,SizeConst=32)]public string Name;[MarshalAs(UnmanagedType.ByValTStr,SizeConst=128)]public string Description;public uint Flags;[MarshalAs(UnmanagedType.ByValTStr,SizeConst=128)]public string Id;[MarshalAs(UnmanagedType.ByValTStr,SizeConst=128)]public string Key; }
 [StructLayout(LayoutKind.Sequential)] struct GUIINFO {public int Size,Flags;public IntPtr Active,Focus,Capture,MenuOwner,MoveSize,Caret;public RECT CaretRect;}
 [StructLayout(LayoutKind.Sequential)] struct BI { public uint Size; public int Width,Height; public ushort Planes,Bits; public uint Compression,ImageSize; public int Xppm,Yppm; public uint Used,Important; }
 [StructLayout(LayoutKind.Sequential)] struct DESC { public uint Width,Height,MipLevels,ArraySize,Format,SampleCount,SampleQuality,Usage,BindFlags,CPUAccessFlags,MiscFlags; }
 [StructLayout(LayoutKind.Sequential)] struct MAPPED { public IntPtr Data; public uint RowPitch,DepthPitch; }
 [StructLayout(LayoutKind.Sequential)] struct BOX { public uint L,T,F,R,B,Bk; }
 delegate bool EnumProc(IntPtr h,IntPtr l);
 delegate bool MonitorProc(IntPtr monitor,IntPtr dc,ref RECT bounds,IntPtr data);
 [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc p,IntPtr l);
 [DllImport("user32.dll")] static extern bool EnumChildWindows(IntPtr parent,EnumProc callback,IntPtr data);
 [DllImport("user32.dll")] static extern bool EnumDisplayMonitors(IntPtr dc,IntPtr clip,MonitorProc callback,IntPtr data);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern bool GetMonitorInfoW(IntPtr monitor,ref MONITORINFOEX info);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern bool EnumDisplayDevicesW(string device,uint index,ref DISPLAYDEVICE info,uint flags);
 [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
 [DllImport("user32.dll")] static extern bool IsWindowEnabled(IntPtr h);
 [DllImport("user32.dll")] static extern bool IsWindow(IntPtr h);
 [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] static extern IntPtr WindowFromPoint(POINT point);
 [DllImport("user32.dll",SetLastError=true)] static extern IntPtr SetWindowsHookExW(int kind,MouseProcedure procedure,IntPtr module,uint thread);
 [DllImport("user32.dll")] static extern bool UnhookWindowsHookEx(IntPtr hook);
 [DllImport("user32.dll")] static extern IntPtr CallNextHookEx(IntPtr hook,int code,IntPtr message,IntPtr data);
 [DllImport("user32.dll")] static extern int GetMessageW(out MESSAGE message,IntPtr window,uint first,uint last);
 [DllImport("user32.dll")] static extern bool PeekMessageW(out MESSAGE message,IntPtr window,uint first,uint last,uint flags);
 [DllImport("user32.dll")] static extern bool TranslateMessage(ref MESSAGE message);
 [DllImport("user32.dll")] static extern IntPtr DispatchMessageW(ref MESSAGE message);
 [DllImport("user32.dll")] static extern bool PostThreadMessageW(uint thread,uint message,UIntPtr w,IntPtr l);
 [DllImport("user32.dll")] static extern bool AttachThreadInput(uint from,uint to,bool attach);
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern IntPtr CreateWaitableTimerExW(IntPtr attributes,string name,uint flags,uint access);
 [DllImport("kernel32.dll",SetLastError=true)] static extern bool SetWaitableTimer(IntPtr timer,ref long due,int period,IntPtr callback,IntPtr state,bool resume);
 [DllImport("kernel32.dll")] static extern uint WaitForSingleObject(IntPtr handle,uint timeout);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
 [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,uint pid);
 [DllImport("kernel32.dll")] static extern bool GetExitCodeProcess(IntPtr process,out uint code);
 [DllImport("kernel32.dll")] static extern bool GetProcessTimes(IntPtr process,out long created,out long exited,out long kernel,out long user);
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool QueryFullProcessImageNameW(IntPtr process,uint flags,StringBuilder name,ref uint length);
 [DllImport("user32.dll")] static extern bool GetKeyboardState([Out]byte[] state);
 [DllImport("user32.dll")] static extern bool SetKeyboardState(byte[] state);
 [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode)] static extern IntPtr GetModuleHandleW(string name);
 [DllImport("user32.dll")] static extern bool GetCursorInfo(ref CURSORINFO info);
 [DllImport("user32.dll")] static extern bool ShowWindowAsync(IntPtr window,int command);
 [DllImport("user32.dll")] static extern bool SetWindowPos(IntPtr window,IntPtr after,int x,int y,int width,int height,uint flags);
 [DllImport("user32.dll")] static extern bool IsIconic(IntPtr h);
 [DllImport("user32.dll")] static extern bool IsChild(IntPtr parent,IntPtr child);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern IntPtr FindWindowExW(IntPtr parent,IntPtr after,string kind,string name);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern int GetWindowText(IntPtr h,StringBuilder t,int n);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr h,StringBuilder t,int n);
 [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h,out uint p);
 [DllImport("user32.dll")] static extern bool GetGUIThreadInfo(uint thread,ref GUIINFO info);
 [DllImport("user32.dll")] static extern IntPtr GetWindow(IntPtr h,uint command);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern IntPtr GetPropW(IntPtr window,string name);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern bool SetPropW(IntPtr window,string name,IntPtr value);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern IntPtr RemovePropW(IntPtr window,string name);
 [DllImport("user32.dll")] static extern bool RedrawWindow(IntPtr window,IntPtr rect,IntPtr region,uint flags);
 [DllImport("user32.dll")] static extern IntPtr GetAncestor(IntPtr h,uint flags);
 [DllImport("user32.dll",EntryPoint="GetWindowLongPtrW")] static extern IntPtr GetWindowLongPtr(IntPtr h,int index);
 [DllImport("user32.dll",EntryPoint="SetWindowLongPtrW",SetLastError=true)] static extern IntPtr SetWindowLongPtr(IntPtr h,int index,IntPtr value);
 [DllImport("user32.dll")] static extern void mouse_event(uint flags,uint dx,uint dy,uint data,UIntPtr extra);
 [DllImport("user32.dll")] static extern int GetSystemMetrics(int index);
 [DllImport("user32.dll")] static extern uint GetDoubleClickTime();
 [DllImport("user32.dll")] static extern bool GetClientRect(IntPtr h,out RECT r);
 [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h,out RECT r);
 [DllImport("user32.dll")] static extern bool ClientToScreen(IntPtr h,ref POINT p);
 [DllImport("user32.dll")] static extern bool ScreenToClient(IntPtr h,ref POINT p);
 [DllImport("user32.dll")] static extern IntPtr ChildWindowFromPointEx(IntPtr h,POINT p,uint flags);
 [DllImport("user32.dll",SetLastError=true,CharSet=CharSet.Unicode)] static extern bool PostMessageW(IntPtr h,uint message,UIntPtr w,IntPtr l);
 [DllImport("user32.dll")] static extern bool SetCursorPos(int x,int y);
 [DllImport("user32.dll")] static extern bool ClipCursor(ref RECT bounds);
 [DllImport("user32.dll")] static extern bool GetClipCursor(out RECT bounds);
 [DllImport("user32.dll",EntryPoint="ClipCursor")] static extern bool UnclipCursor(IntPtr bounds);
 [DllImport("Magnification.dll")] static extern bool MagInitialize();
 [DllImport("Magnification.dll")] static extern bool MagShowSystemCursor(bool show);
 [DllImport("Magnification.dll")] static extern bool MagUninitialize();
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern uint MapVirtualKeyW(uint code,uint map);
 [DllImport("user32.dll")] static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
 [DllImport("user32.dll")] static extern IntPtr GetWindowDpiAwarenessContext(IntPtr window);
 [DllImport("user32.dll")] static extern bool LogicalToPhysicalPointForPerMonitorDPI(IntPtr window,ref POINT point);
 [DllImport("user32.dll")] static extern bool PrintWindow(IntPtr h,IntPtr dc,uint flags);
 [DllImport("user32.dll")] static extern bool GetWindowDisplayAffinity(IntPtr h,out uint affinity);
 [DllImport("user32.dll")] static extern IntPtr GetDC(IntPtr h);
 [DllImport("user32.dll")] static extern int ReleaseDC(IntPtr h,IntPtr dc);
 [DllImport("dwmapi.dll")] static extern int DwmGetWindowAttribute(IntPtr h,int a,out int value,int size);
 [DllImport("dwmapi.dll",EntryPoint="DwmGetWindowAttribute")] static extern int DwmBounds(IntPtr h,int a,out RECT value,int size);
 [DllImport("gdi32.dll")] static extern IntPtr CreateCompatibleDC(IntPtr dc);
 [DllImport("gdi32.dll")] static extern IntPtr CreateCompatibleBitmap(IntPtr dc,int w,int h);
 [DllImport("gdi32.dll")] static extern IntPtr SelectObject(IntPtr dc,IntPtr o);
 [DllImport("gdi32.dll")] static extern bool DeleteObject(IntPtr o);
 [DllImport("gdi32.dll")] static extern bool DeleteDC(IntPtr dc);
 [DllImport("gdi32.dll")] static extern int SetStretchBltMode(IntPtr dc,int mode);
 [DllImport("gdi32.dll")] static extern bool StretchBlt(IntPtr d,int x,int y,int w,int h,IntPtr s,int sx,int sy,int sw,int sh,uint rop);
 [DllImport("gdi32.dll")] static extern int GetDIBits(IntPtr dc,IntPtr b,uint start,uint rows,byte[] data,ref BI info,uint use);
 [DllImport("d3d11.dll")] static extern int D3D11CreateDevice(IntPtr adapter,int driver,IntPtr software,uint flags,IntPtr levels,uint count,uint sdk,out IntPtr device,out int level,out IntPtr context);
 [DllImport("d3d11.dll")] static extern int CreateDirect3D11DeviceFromDXGIDevice(IntPtr device,out IntPtr inspectable);
 [ComImport,Guid("3628E81B-3CAC-4C60-B7F4-23CE0E0C3356"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface ItemInterop {
  IntPtr CreateForWindow(IntPtr window,ref Guid iid); IntPtr CreateForMonitor(IntPtr monitor,ref Guid iid);
 }
 [ComImport,Guid("A9B3D012-3DF2-4EE3-B8D1-8695F457D3C1"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface DxgiAccess { void GetInterface(ref Guid iid,out IntPtr value); }
 [UnmanagedFunctionPointer(CallingConvention.StdCall)] delegate int CreateTexture(IntPtr self,ref DESC desc,IntPtr initial,out IntPtr texture);
 [UnmanagedFunctionPointer(CallingConvention.StdCall)] delegate void GetDesc(IntPtr self,out DESC desc);
 [UnmanagedFunctionPointer(CallingConvention.StdCall)] delegate void CopyRegion(IntPtr self,IntPtr dest,uint sub,uint x,uint y,uint z,IntPtr source,uint sourceSub,ref BOX region);
 [UnmanagedFunctionPointer(CallingConvention.StdCall)] delegate int Map(IntPtr self,IntPtr resource,uint sub,uint type,uint flags,out MAPPED mapped);
 [UnmanagedFunctionPointer(CallingConvention.StdCall)] delegate void Unmap(IntPtr self,IntPtr resource,uint sub);
 [UnmanagedFunctionPointer(CallingConvention.StdCall)] delegate void FlushContext(IntPtr self);
 static T Method<T>(IntPtr self,int index)where T:class{return Marshal.GetDelegateForFunctionPointer(Marshal.ReadIntPtr(Marshal.ReadIntPtr(self),index*IntPtr.Size),typeof(T))as T;}
 static readonly object OutputLock=new object(),InputLock=new object();static BinaryWriter Writer;static volatile bool Quitting;static long FrameNumber,lastActivity;static int CurrentBackend,PendingInputStatus,PendingInputFailure;
 sealed class OutputRecord {public int Status,W,H,CW,CH,Backend,Hash;public byte[] Bytes;public long Number,Time;}
 static readonly AutoResetEvent OutputReady=new AutoResetEvent(false);
 static OutputRecord PendingFrame,PendingStatus,PendingCaret;static Thread OutputThread;static volatile bool OutputStopping;
 static readonly Stack<byte[]> PixelBuffers=new Stack<byte[]>();
 static byte[] RentPixels(int length){lock(PixelBuffers){
  if(PixelBuffers.Count!=0){byte[] first=PixelBuffers.Pop();if(first.Length==length)return first;
   if(PixelBuffers.Count!=0){byte[] second=PixelBuffers.Pop();PixelBuffers.Push(first);if(second.Length==length)return second;PixelBuffers.Push(second);}
   else PixelBuffers.Push(first);
  }
 }return new byte[length];}
 static void ReturnPixels(byte[] bytes){if(bytes==null)return;lock(PixelBuffers){if(PixelBuffers.Count<2)PixelBuffers.Push(bytes);}}
 static void ReleaseRecord(OutputRecord record){if(record!=null&&(record.Status==1||record.Status==8)){ReturnPixels(record.Bytes);record.Bytes=null;}}
 // The pipe may block. Only this background writer owns it; capture and input
 // never wait for a slow consumer or retain compositor buffers while writing.
 static void StartOutput(){OutputStopping=false;OutputThread=new Thread(OutputLoop);OutputThread.IsBackground=true;OutputThread.Start();}
 static void StopOutput(){OutputStopping=true;OutputReady.Set();if(OutputThread!=null)OutputThread.Join(250);}
 static void OutputLoop(){try{while(true){OutputReady.WaitOne();OutputRecord status,frame,caret;
   lock(OutputLock){status=PendingStatus;PendingStatus=null;frame=PendingFrame;PendingFrame=null;caret=PendingCaret;PendingCaret=null;}
   if(status!=null)WriteRecord(status);
   if(caret!=null)WriteRecord(caret);
   FlushInputStatus((DateTime.UtcNow.Ticks-621355968000000000L)/10000);
   if(frame!=null)try{WriteRecord(frame);}finally{ReleaseRecord(frame);}Writer.Flush();
   if(OutputStopping){lock(OutputLock){if(PendingStatus==null&&PendingFrame==null)break;}OutputReady.Set();}
  }}catch{Quitting=true;}finally{lock(OutputLock){ReleaseRecord(PendingFrame);PendingStatus=PendingFrame=null;}}}
 static OutputRecord Record(int status,int w,int h,int cw,int ch,byte[] bytes,long number,int hash,long time){return new OutputRecord{Status=status,W=w,H=h,CW=cw,CH=ch,Bytes=bytes,Number=number,Hash=hash,Time=time,Backend=CurrentBackend};}
 static IntPtr Selected;static int SelectedPid,OwnerPid;static bool AllowInput,SelectedMonitor,ImeCapture;static string SelectedSourceId="";static readonly HashSet<int> HeldKeys=new HashSet<int>();static readonly Dictionary<int,IntPtr> HeldButtons=new Dictionary<int,IntPtr>(),HeldKeyTargets=new Dictionary<int,IntPtr>();static IntPtr KeyTarget,HoverTarget,LogicalFocus;static int LastX,LastY,ClickX,ClickY,ClickButton=-1;static IntPtr ClickTarget;static long ClickAt;
 sealed class ViewIdentity {public IntPtr Window;public int Pid;public uint Thread;public long Start;}
 const int MaxViews=128;const long Topmost=8;
 sealed class ViewState {public ViewIdentity Identity;public bool PlacementChanged,OriginalTopmost,AddedNoActivate;}
 static ViewIdentity ActiveView;static readonly Dictionary<IntPtr,ViewState> Views=new Dictionary<IntPtr,ViewState>();static readonly Dictionary<int,IntPtr> FollowProcesses=new Dictionary<int,IntPtr>();
 static string Enc(string s){return Convert.ToBase64String(Encoding.UTF8.GetBytes(s));}
 static bool Cloaked(IntPtr h){int value;return DwmGetWindowAttribute(h,14,out value,4)==0&&value!=0;}
 static bool OwnerAlive(long pid){try{if(pid<=0)return false;using(Process process=Process.GetProcessById((int)pid))return !process.HasExited;}catch{return false;}}
 static void Dpi(){try{SetThreadDpiAwarenessContext(new IntPtr(-4));}catch(EntryPointNotFoundException){}}
 static bool WallpaperWindow(IntPtr window){StringBuilder kind=new StringBuilder(128);GetClassName(window,kind,kind.Capacity);return kind.ToString()=="Progman"||kind.ToString()=="WorkerW";}
 static string ProcessName(uint pid){IntPtr process=OpenProcess(0x1000,false,pid);if(process==IntPtr.Zero)return "";
  try{StringBuilder name=new StringBuilder(32768);uint length=(uint)name.Capacity;return QueryFullProcessImageNameW(process,0,name,ref length)?Path.GetFileNameWithoutExtension(name.ToString()):"";}
  finally{CloseHandle(process);}
 }
 static bool ProcessStart(IntPtr process,out long start){uint code;long exited,kernel,user;start=0;return GetExitCodeProcess(process,out code)&&code==259&&GetProcessTimes(process,out start,out exited,out kernel,out user);}
 static string MonitorIdentity(string device){DISPLAYDEVICE info=new DISPLAYDEVICE{Size=Marshal.SizeOf(typeof(DISPLAYDEVICE))};return EnumDisplayDevicesW(device,0,ref info,1)&&!String.IsNullOrEmpty(info.Id)?device+"|"+info.Id:"";}
 static bool MonitorInfo(out MONITORINFOEX info){info=new MONITORINFOEX{Size=Marshal.SizeOf(typeof(MONITORINFOEX))};return SelectedMonitor&&SelectedSourceId.Length>0&&GetMonitorInfoW(Selected,ref info)&&String.Equals(MonitorIdentity(info.Device),SelectedSourceId,StringComparison.OrdinalIgnoreCase);}
 public static void List(long owner){Dpi();EnumDisplayMonitors(IntPtr.Zero,IntPtr.Zero,delegate(IntPtr monitor,IntPtr dc,ref RECT bounds,IntPtr data){MONITORINFOEX info=new MONITORINFOEX{Size=Marshal.SizeOf(typeof(MONITORINFOEX))};if(!GetMonitorInfoW(monitor,ref info))return true;
  string identity=MonitorIdentity(info.Device);if(identity.Length==0)return true;
  Console.WriteLine(monitor.ToInt64()+"\t0\t"+Enc(info.Device)+"\t"+Enc("Desktop 路 "+info.Device+" 路 "+(info.Monitor.R-info.Monitor.L)+"脳"+(info.Monitor.B-info.Monitor.T))+"\t0\tmonitor\t"+Enc(identity));return true;},IntPtr.Zero);
  EnumWindows(delegate(IntPtr h,IntPtr l){if(!IsWindowVisible(h)||Cloaked(h)||WallpaperWindow(h))return true;uint pid;GetWindowThreadProcessId(h,out pid);if(pid==owner||pid==Process.GetCurrentProcess().Id)return true;
  StringBuilder title=new StringBuilder(2048);GetWindowText(h,title,title.Capacity);string process=ProcessName(pid);if(process.Length==0)return true;
  // Player windows and app-owned popup views often have no title. Expose those
  // separately, but do not turn desktop/system utility surfaces into sources.
  if(title.Length==0){if(GetWindow(h,4)==IntPtr.Zero)return true;RECT r;if(!GetClientRect(h,out r)||r.R<64||r.B<48)return true;StringBuilder kind=new StringBuilder(128);GetClassName(h,kind,kind.Capacity);title.Append(process+" 路 "+kind);}
  Console.WriteLine(h.ToInt64()+"\t"+pid+"\t"+Enc(process)+"\t"+Enc(title.ToString())+"\t"+(IsIconic(h)?"1":"0")+"\twindow\t");return true;},IntPtr.Zero);}
 static ViewIdentity Identity(IntPtr window){uint pid;uint thread=GetWindowThreadProcessId(window,out pid);if(window==IntPtr.Zero||thread==0||pid<=4||pid==OwnerPid&&!(ImeCapture&&ImeWindow(window))||pid==Process.GetCurrentProcess().Id)return null;
  IntPtr process;if(!FollowProcesses.TryGetValue((int)pid,out process)){if(FollowProcesses.Count>=256)return null;
   // Window capture needs identity queries, never full access to the source process.
   process=OpenProcess(0x1000,false,pid);if(process==IntPtr.Zero){if(window==Selected)Console.Error.WriteLine("Window identity query failed: Win32 "+Marshal.GetLastWin32Error());return null;}FollowProcesses.Add((int)pid,process);
  }long start;if(!ProcessStart(process,out start))return null;return new ViewIdentity{Window=window,Pid=(int)pid,Thread=thread,Start=start};}
 static bool ValidView(ViewIdentity view){if(view==null||!IsWindow(view.Window))return false;uint pid;uint thread=GetWindowThreadProcessId(view.Window,out pid);IntPtr process;long start;return pid==view.Pid&&thread==view.Thread&&FollowProcesses.TryGetValue(view.Pid,out process)&&ProcessStart(process,out start)&&start==view.Start;}
 static ViewState Remember(IntPtr window){ViewState state;if(Views.TryGetValue(window,out state)){if(ValidView(state.Identity))return state;Views.Remove(window);}if(Views.Count>=MaxViews)return null;ViewIdentity identity=Identity(window);if(identity==null)return null;state=new ViewState{Identity=identity};Views.Add(window,state);return state;}
 // The selected HWND stays fixed; owned player toolbars must never replace it.
 static void InitializeSource(){if(SelectedMonitor)return;ActiveView=Identity(Selected);Remember(Selected);NativePointerSource=WindowClass(Selected)=="CabinetWClass";}
 static void PlaceBehindGame(IntPtr window,IntPtr game){
  if(window==IntPtr.Zero||game==IntPtr.Zero||window==game||!IsWindow(game))return;
  long gameStyle=GetWindowLongPtr(game,-20).ToInt64(),sourceStyle=GetWindowLongPtr(window,-20).ToInt64();
  bool topmost=(sourceStyle&Topmost)!=0,desired=(gameStyle&Topmost)!=0;
  if(topmost!=desired){ViewState state=Remember(window);if(state==null)return;
   if(!state.PlacementChanged){state.OriginalTopmost=topmost;state.PlacementChanged=true;}
   SetWindowPos(window,new IntPtr(desired?-1:-2),0,0,0,0,0x213);
  }
  if(GetWindow(window,3)!=game)SetWindowPos(window,game,0,0,0,0,0x213);
 }
 static void RestorePlacement(){lock(InputLock){foreach(ViewState state in Views.Values){if(!state.PlacementChanged||!ValidView(state.Identity))continue;state.PlacementChanged=false;SetWindowPos(state.Identity.Window,new IntPtr(state.OriginalTopmost?-1:-2),0,0,0,0,0x213);}}}
 static long NextActivity;
 const uint PointerMarker=0x4B575247;
 static IntPtr NoActivateWindow;static bool NativePointerSource;static volatile bool NativePointerReady,NativeGameInput;static int DirectButtons;
 sealed class PointerArea {public IntPtr Window;public RECT Bounds;public bool Contains(POINT point){return point.X>=Bounds.L&&point.X<Bounds.R&&point.Y>=Bounds.T&&point.Y<Bounds.B;}}
 static volatile PointerArea NativeArea,ImeArea;
 static void PreventSourceActivation(){
  if(ImeCapture||SelectedMonitor||!ValidView(ActiveView))return;
  NoActivateWindow=Selected;PreventActivation(Selected);
  foreach(IntPtr window in OwnedWindows)PreventActivation(window);
  NativePointerReady=NativePointerSource&&ControlRelay()!=IntPtr.Zero&&(GetWindowLongPtr(Selected,-20).ToInt64()&0x08000000)!=0;
 }
 static void PreventActivation(IntPtr window){ViewState state=Remember(window);if(state==null)return;long style=GetWindowLongPtr(window,-20).ToInt64();if((style&0x08000000)==0){SetWindowLongPtr(window,-20,new IntPtr(style|0x08000000));SetWindowPos(window,IntPtr.Zero,0,0,0,0,0x237);state.AddedNoActivate=true;}}
 static void RestoreSourceActivation(){
  NativePointerReady=false;NativeArea=null;
  foreach(ViewState state in Views.Values)if(state.AddedNoActivate&&ValidView(state.Identity)){state.AddedNoActivate=false;IntPtr window=state.Identity.Window;SetWindowLongPtr(window,-20,new IntPtr(GetWindowLongPtr(window,-20).ToInt64()&~0x08000000L));SetWindowPos(window,IntPtr.Zero,0,0,0,0,0x237);}
  NoActivateWindow=IntPtr.Zero;
 }
 static bool SourcePointerHit(POINT point){
  IntPtr hit=WindowFromPoint(point);if(ValidTarget(hit))return true;
  uint pid;GetWindowThreadProcessId(hit,out pid);if(pid!=SelectedPid)return false;
  for(int depth=0;hit!=IntPtr.Zero&&depth<8;depth++){hit=GetWindow(GetAncestor(hit,2),4);if(hit==Selected)return true;}
  return false;
 }
 static bool RefreshNativePointer(int x,int y){
  if(!NativePointerReady||!GameForeground()||ControlRelay()==IntPtr.Zero||GetPropW(GetForegroundWindow(),"kwrg_projection_entry_release")!=IntPtr.Zero)return false;
  POINT screen=new POINT(x,y);if(!ClientToScreen(Selected,ref screen))return false;
  if(!SourcePointerHit(screen)){NativeArea=null;return false;}
  IntPtr hit=WindowFromPoint(screen);RECT bounds;if(!GetWindowRect(hit,out bounds))return false;NativeArea=new PointerArea{Window=hit,Bounds=bounds};
  // WinUI consumes the system pointer stream; a posted WM_MOUSEMOVE has no pointer data.
  return MoveNativePointer(screen);
 }
 static bool MoveNativePointer(POINT screen){
  int left=GetSystemMetrics(76),top=GetSystemMetrics(77),width=GetSystemMetrics(78),height=GetSystemMetrics(79);
  if(width<=0||height<=0||screen.X<left||screen.Y<top||screen.X>=left+width||screen.Y>=top+height)return false;
  uint x=(uint)(((long)(screen.X-left)*65536+32768)/width),y=(uint)(((long)(screen.Y-top)*65536+32768)/height);
  mouse_event(0xE001,x,y,0,new UIntPtr(PointerMarker));return true;
 }
 static void KeepSourceActive(){
  if(SelectedMonitor||NativeGameInput)return;long now=Stopwatch.GetTimestamp();if(now<NextActivity)return;NextActivity=now+Stopwatch.Frequency/4;
  lock(InputLock){
   if(ImeCapture)return;
   if(Quitting||!GameForeground()||!ValidView(ActiveView)||ActiveView.Window!=Selected){RestoreSourceActivation();RestorePlacement();return;}
   if(IsIconic(Selected)||!IsWindowVisible(Selected)){if(!ImeCapture){PreventSourceActivation();ShowWindowAsync(Selected,4);}return;} // Never reopen a closed candidate UI.
   if(ControlRelay()!=IntPtr.Zero)PreventSourceActivation();else RestoreSourceActivation();
   if(State()!=0)return;
   IntPtr game=GetForegroundWindow(),ime=GetPropW(game,"kwrg_projection_ime");
   // System UI can occupy a higher compositor band; never put the source above the game with it.
   PlaceBehindGame(Selected,!ImeCapture&&ime!=IntPtr.Zero&&ime!=Selected&&IsWindowVisible(ime)&&ImeWindow(ime)&&GetWindow(ime,3)==game?ime:game);
   for(int i=OwnedWindows.Length-1;i>=0;i--)PlaceBehindGame(OwnedWindows[i],game);
   RedrawWindow(Selected,IntPtr.Zero,IntPtr.Zero,0x481); // INVALIDATE | ALLCHILDREN | FRAME; never wait for target painting.
  }
 }
 static void WriteRecord(OutputRecord r){Writer.Write(0x33534341);Writer.Write(r.Status);Writer.Write(r.W);Writer.Write(r.H);Writer.Write(r.CW);Writer.Write(r.CH);Writer.Write(r.Bytes==null?0:r.Bytes.Length);Writer.Write(r.Backend);Writer.Write(r.Hash);Writer.Write(r.Number);Writer.Write(r.Time);if(r.Bytes!=null)Writer.Write(r.Bytes);}
 static void FlushInputStatus(long time){int failure=Interlocked.Exchange(ref PendingInputFailure,0),status=Interlocked.Exchange(ref PendingInputStatus,0);if(failure!=0)status=failure;if(status!=0)WriteRecord(Record(status,0,0,0,0,null,0,0,time));}
 static int Hash(byte[] bytes){unchecked{uint hash=2166136261;for(int i=0;i<bytes.Length;i+=Math.Max(4,(bytes.Length/32768/4)*4)){hash=(hash^bytes[i])*16777619;hash=(hash^bytes[Math.Min(i+1,bytes.Length-1)])*16777619;hash=(hash^bytes[Math.Min(i+2,bytes.Length-1)])*16777619;}return(int)hash;}}
 static void Packet(int status,int w,int h,int cw,int ch,byte[] bytes,long captured=0,int originX=0,int originY=0,bool canvas=false){
  if(status>=100){if(status>100&&status!=103)Interlocked.Exchange(ref PendingInputFailure,status);else Interlocked.Exchange(ref PendingInputStatus,status);OutputReady.Set();return;}
  bool positioned=canvas||ImeCapture||originX!=0||originY!=0;
  long number=bytes==null?0:Interlocked.Increment(ref FrameNumber);int hash=bytes==null?0:positioned?((originY&65535)<<16)|(originX&65535):Hash(bytes);long time=captured>0?captured:(DateTime.UtcNow.Ticks-621355968000000000L)/10000;
  OutputRecord record=Record(status,w,h,cw,ch,bytes,number,hash,time);
  if(positioned&&!ImeCapture&&bytes!=null)record.Backend=4;
  if(canvas)record.Backend=5;
  lock(OutputLock){if(bytes!=null){ReleaseRecord(PendingFrame);PendingFrame=record;PendingStatus=null;}else PendingStatus=record;}OutputReady.Set();
 }
 static int State(){uint pid;uint affinity;RECT rect;if(SelectedMonitor){MONITORINFOEX info;if(!MonitorInfo(out info))return 3;rect=info.Monitor;int width=rect.R-rect.L,height=rect.B-rect.T;return width<2||height<2||width>MaxEdge||height>MaxEdge||(long)width*height>MaxPixels?6:0;}
  if(!ValidView(ActiveView)||!IsWindow(Selected)||WallpaperWindow(Selected))return 3;GetWindowThreadProcessId(Selected,out pid);if(pid!=SelectedPid)return 3;
  if(IsIconic(Selected))return 2;if(Cloaked(Selected))return 4;if(GetWindowDisplayAffinity(Selected,out affinity)&&affinity!=0)return 5;
  if(!GetClientRect(Selected,out rect)||rect.R-rect.L<2||rect.B-rect.T<2||rect.R-rect.L>MaxEdge||rect.B-rect.T>MaxEdge||(long)(rect.R-rect.L)*(rect.B-rect.T)>MaxPixels)return 6;return 0;}
 static void Dimensions(int sourceW,int sourceH,int edge,int pixels,bool native,out int w,out int h){if(native){w=sourceW;h=sourceH;return;}double scale=Math.Min(1,Math.Min(edge/(double)Math.Max(sourceW,sourceH),Math.Sqrt(pixels/(double)((long)sourceW*sourceH))));w=Math.Max(2,(int)Math.Floor(sourceW*scale));h=Math.Max(2,(int)Math.Floor(sourceH*scale));}
 static int Delay(int w,int h,int fps){return fps==0?1:Math.Max(1,1000/Math.Max(1,fps));}
 static bool ValidTarget(IntPtr target){uint pid;if(SelectedMonitor||target==IntPtr.Zero||!IsWindow(target))return false;IntPtr root=GetAncestor(target,2);if(target!=Selected&&!IsChild(Selected,target)&&!IsOwnedWindow(root))return false;GetWindowThreadProcessId(target,out pid);return pid!=0&&pid!=OwnerPid&&pid!=Process.GetCurrentProcess().Id;}
 static bool GameForeground(){uint pid;IntPtr foreground=GetForegroundWindow();return foreground!=IntPtr.Zero&&GetWindowThreadProcessId(foreground,out pid)!=0&&pid==OwnerPid;}
 static string WindowClass(IntPtr window){StringBuilder name=new StringBuilder(128);GetClassName(window,name,name.Capacity);return name.ToString();}
 static bool ChromiumInputHost(IntPtr window){return WindowClass(window)=="Chrome_RenderWidgetHostHWND";}
 static bool ImeWindow(IntPtr window){string kind=WindowClass(window);if(kind.StartsWith("Microsoft.IME.",StringComparison.Ordinal)||kind.StartsWith("mscandui",StringComparison.OrdinalIgnoreCase)||kind.StartsWith("MSCTFIME ",StringComparison.Ordinal)||kind=="CiceroUIWndFrame"||kind=="Windows.UI.Core.CoreWindow"||kind=="Windows.UI.Input.InputSite.WindowClass")return true;
  uint pid;GetWindowThreadProcessId(window,out pid);try{using(Process process=Process.GetProcessById((int)pid)){string name=process.ProcessName;return name.Equals("TextInputHost",StringComparison.OrdinalIgnoreCase)||name.Equals("ChsIME",StringComparison.OrdinalIgnoreCase)||name.Equals("ctfmon",StringComparison.OrdinalIgnoreCase);}}catch{return false;}
 }
 static IntPtr RendererAtPoint(IntPtr branch,POINT screen){
  // Chromium deliberately marks its legacy input HWND WS_EX_TRANSPARENT. It
  // still handles mouse and wheel messages. Generic CWP_SKIPTRANSPARENT hit
  // testing skips that real receiver and can hit a video-only D3D sibling.
  // Permit only the known input class, inside the selected app and point.
  IntPtr found=IntPtr.Zero;int visited=0;
  EnumChildWindows(branch,delegate(IntPtr child,IntPtr ignored){
   if(++visited>256)return false;
   if(!ValidTarget(child)||!IsWindowVisible(child)||!IsWindowEnabled(child)||!ChromiumInputHost(child))return true;
   POINT local=screen;RECT rect;if(!ScreenToClient(child,ref local)||!GetClientRect(child,out rect)||local.X<rect.L||local.Y<rect.T||local.X>=rect.R||local.Y>=rect.B)return true;
   found=child;return false;
  },IntPtr.Zero);return found;
 }
 static IntPtr Target(ref POINT point){
  POINT screen=point;if(!ClientToScreen(Selected,ref screen))return Selected;
  IntPtr root=RootAtPoint(screen);if(root==IntPtr.Zero)return IntPtr.Zero;
  // Swing roots may contain an unrelated JCEF view behind the pointed editor.
  IntPtr renderer=WindowClass(root).StartsWith("SunAwt",StringComparison.Ordinal)?IntPtr.Zero:RendererAtPoint(root,screen);
  if(renderer!=IntPtr.Zero){point=screen;ScreenToClient(renderer,ref point);return renderer;}
  IntPtr current=root;int budget=384;
  // Walk siblings in Z order. D3D presenters are visual-only; do not stop at
  // one or search an entire subtree ahead of an overlapping real control.
  for(int depth=0;depth<16&&budget>0;depth++){
   IntPtr chosen=IntPtr.Zero;
   for(IntPtr child=GetWindow(current,5);child!=IntPtr.Zero&&budget-->0;child=GetWindow(child,2)){
    if(!ValidTarget(child)||!IsWindowVisible(child)||!IsWindowEnabled(child))continue;
    POINT local=screen;RECT rect;if(!ScreenToClient(child,ref local)||!GetClientRect(child,out rect)||local.X<rect.L||local.Y<rect.T||local.X>=rect.R||local.Y>=rect.B)continue;
    string kind=WindowClass(child);if(kind=="Intermediate D3D Window")continue;
    bool transparent=(GetWindowLongPtr(child,-20).ToInt64()&0x20)!=0;
    if(transparent&&kind!="Chrome_RenderWidgetHostHWND"&&!kind.StartsWith("CefBrowser",StringComparison.Ordinal))continue;
    chosen=child;break;
   }
   if(chosen==IntPtr.Zero)break;current=chosen;
  }
  point=screen;ScreenToClient(current,ref point);return current;
 }
 static IntPtr Coords(int x,int y){return new IntPtr(unchecked((int)(((uint)(ushort)y<<16)|(ushort)x)));}
 static uint Flags(){uint flags=0;foreach(int button in HeldButtons.Keys)flags|=button==0?1u:button==1?2u:button==2?16u:button==3?32u:64u;if(HeldKeys.Contains(0xA0)||HeldKeys.Contains(0xA1)||HeldKeys.Contains(0x10))flags|=4;if(HeldKeys.Contains(0xA2)||HeldKeys.Contains(0xA3)||HeldKeys.Contains(0x11))flags|=8;return flags;}
 static bool Post(IntPtr target,uint message,uint w,IntPtr l){return ValidTarget(target)&&PostMessageW(target,message,new UIntPtr(w),l);}
 static IntPtr MouseTimer;
 static void WaitMouseMove(){
  // A normal Sleep(2) rounds to a scheduler tick; this timer lets AWT consume its move in 2ms.
  if(MouseTimer==IntPtr.Zero)MouseTimer=CreateWaitableTimerExW(IntPtr.Zero,null,2,0x1F0003);
  long due=-20000;
  if(MouseTimer==IntPtr.Zero||!SetWaitableTimer(MouseTimer,ref due,0,IntPtr.Zero,IntPtr.Zero,false)||WaitForSingleObject(MouseTimer,100)!=0)throw new IOException("Mouse synchronization timer failed");
 }
 static bool MousePost(IntPtr target,uint message,uint flags,IntPtr point){
  if(!ValidTarget(target))return false;
  if(!WindowClass(target).StartsWith("SunAwt",StringComparison.Ordinal))return Post(target,message,flags,point);
  POINT screen=new POINT(unchecked((short)point.ToInt64()),unchecked((short)(point.ToInt64()>>16)));if(!ClientToScreen(target,ref screen))return false;
  // AWT consumes the queued message's screen position instead of its client-coordinate lParam.
  point=Coords(screen.X,screen.Y);
  if(message==0x200&&(flags&0x73)==0||!GameForeground()||ControlRelay()==IntPtr.Zero)return Post(target,message,flags,point);
  // AWT reads GetMessagePos/GetKeyState rather than trusting the posted mouse coordinates/modifiers.
  // Share only sender/source state while AWT consumes this queued event; never join Minecraft's input queue.
  uint pid;uint sourceThread=GetWindowThreadProcessId(target,out pid),sender=GetCurrentThreadId();MESSAGE ignored;PeekMessageW(out ignored,IntPtr.Zero,0,0,0);
  if(sourceThread==0||!AttachThreadInput(sender,sourceThread,true))return false;
  byte[] saved=new byte[256];bool captured=GetKeyboardState(saved);
  try{
   if(!captured)return false;byte[] state=(byte[])saved.Clone();int[] keys={1,2,4,5,6,0x10,0x11};uint[] masks={1,2,16,32,64,4,8};
   for(int i=0;i<keys.Length;i++)state[keys[i]]=(byte)((state[keys[i]]&1)|((flags&masks[i])!=0?128:0));
   if(!SetKeyboardState(state))return false;
   if(GameForeground()&&ControlRelay()!=IntPtr.Zero)SetCursorPos(screen.X,screen.Y);
   bool sent=Post(target,message,flags,point);
   WaitMouseMove();
   return sent;
  }finally{if(captured)SetKeyboardState(saved);AttachThreadInput(sender,sourceThread,false);}
 }
 static uint Scan(int vk){return MapVirtualKeyW((uint)vk,4);}
 static IntPtr KeyBits(int vk,bool up,bool repeat){uint scan=Scan(vk);uint bits=1|((scan&255)<<16);if((scan&0xFF00)!=0)bits|=1u<<24;if(HeldKeys.Contains(0xA4)||HeldKeys.Contains(0xA5))bits|=1u<<29;if(repeat||up)bits|=1u<<30;if(up)bits|=1u<<31;return new IntPtr(unchecked((int)bits));}
 static bool IsAlt(int vk){return vk==0xA4||vk==0xA5||HeldKeys.Contains(0xA4)||HeldKeys.Contains(0xA5);}
 static void FocusView(IntPtr target){
  if(NativePointerSource)return;
  if(!ValidTarget(target)||LogicalFocus==target)return;
  // These are view notifications only; they never activate the source window.
  if(ValidTarget(LogicalFocus))Post(LogicalFocus,0x8,0,IntPtr.Zero);
  LogicalFocus=target;Post(target,0x7,0,IntPtr.Zero);
 }
 static IntPtr KeyboardTarget(){
  foreach(IntPtr window in OwnedWindows){uint childPid;uint childThread=GetWindowThreadProcessId(window,out childPid);GUIINFO childInfo=new GUIINFO{Size=Marshal.SizeOf(typeof(GUIINFO))};if(childThread!=0&&GetGUIThreadInfo(childThread,ref childInfo)&&ValidTarget(childInfo.Focus)&&GetAncestor(childInfo.Focus,2)==window)return childInfo.Focus;}
  uint pid;uint thread=GetWindowThreadProcessId(Selected,out pid);GUIINFO info=new GUIINFO{Size=Marshal.SizeOf(typeof(GUIINFO))};
  if(thread!=0&&GetGUIThreadInfo(thread,ref info)&&ValidTarget(info.Focus))return info.Focus;
  if(ValidTarget(KeyTarget))return KeyTarget;
  if(ValidTarget(LogicalFocus))return LogicalFocus;return Selected;
 }
 static POINT CaretClick;static IntPtr CaretClickWindow;static bool CaretClickKnown;static int CaretPending,CaretRevision;
 static void CaretPacket(string value){byte[] bytes=Encoding.UTF8.GetBytes(value);lock(OutputLock){PendingCaret=Record(22,0,0,0,0,bytes,0,0,(DateTime.UtcNow.Ticks-621355968000000000L)/10000);}OutputReady.Set();}
 static void PublishCaret(){
  Dpi();
  if(!GameForeground()||ControlRelay()==IntPtr.Zero)return;
  IntPtr target=KeyboardTarget();uint pid;uint thread=GetWindowThreadProcessId(target,out pid);GUIINFO info=new GUIINFO{Size=Marshal.SizeOf(typeof(GUIINFO))};string value="0";
  if(thread!=0&&GetGUIThreadInfo(thread,ref info)&&ValidTarget(info.Caret)){
   POINT point=new POINT(info.CaretRect.L,info.CaretRect.T),bottom=new POINT(info.CaretRect.L,info.CaretRect.B);
   if(CaretScreen(info.Caret,ref point,ref bottom)){int height=bottom.Y-point.Y;
    if(height>0&&height<=1024&&ScreenToClient(Selected,ref point)&&Math.Abs((long)point.X)<=MaxEdge&&Math.Abs((long)point.Y)<=MaxEdge)value=point.X+"\t"+point.Y+"\t"+height;
   }
  }
  if(value!="0"||!CaretClickKnown){Interlocked.Increment(ref CaretRevision);CaretPacket(value);return;}
  if(Interlocked.CompareExchange(ref CaretPending,1,0)!=0)return;
  IntPtr root=GetAncestor(target,2);uint rootPid;GetWindowThreadProcessId(root,out rootPid);
  POINT click=CaretClick;if(!ValidTarget(CaretClickWindow)||!ClientToScreen(CaretClickWindow,ref click)){Interlocked.Exchange(ref CaretPending,0);CaretPacket("0");return;}
  int revision=Volatile.Read(ref CaretRevision);
  bool queued=CheatProgramWindowScroll.QueueCaret(root,(int)rootPid,target,click.X,click.Y,
   delegate{return !Quitting&&GameForeground()&&ControlRelay()!=IntPtr.Zero&&ValidTarget(target);},
   delegate(int[] rect){try{lock(InputLock){if(revision!=Volatile.Read(ref CaretRevision))return;if(rect==null){CaretPacket("0");return;}POINT point=new POINT(rect[0],rect[1]);if(ScreenToClient(Selected,ref point)&&Math.Abs((long)point.X)<=MaxEdge&&Math.Abs((long)point.Y)<=MaxEdge)CaretPacket(point.X+"\t"+point.Y+"\t"+rect[2]);}}finally{Interlocked.Exchange(ref CaretPending,0);}});
  if(!queued)Interlocked.Exchange(ref CaretPending,0);
 }
 static bool CaretScreen(IntPtr window,ref POINT top,ref POINT bottom){
  IntPtr previous=SetThreadDpiAwarenessContext(GetWindowDpiAwarenessContext(window));
  try{return ClientToScreen(window,ref top)&&ClientToScreen(window,ref bottom)&&LogicalToPhysicalPointForPerMonitorDPI(window,ref top)&&LogicalToPhysicalPointForPerMonitorDPI(window,ref bottom);}
  finally{if(previous!=IntPtr.Zero)SetThreadDpiAwarenessContext(previous);}
 }
 static void Release(){
  NativeGameInput=false;
  ImeArea=null;
  CaretClickKnown=false;
  CaretClickWindow=IntPtr.Zero;
  Interlocked.Increment(ref CaretRevision);
  if(NativePointerSource&&HeldButtons.Count>0)Post(Selected,0x1F,0,IntPtr.Zero);
  RestoreSourceActivation();
  RestoreControlCursor();
  CheatProgramWindowScroll.Clear();
  if(HoverTarget!=IntPtr.Zero)Post(HoverTarget,0x2A3,0,IntPtr.Zero);HoverTarget=IntPtr.Zero;
  foreach(KeyValuePair<int,IntPtr> button in new List<KeyValuePair<int,IntPtr>>(HeldButtons)){if(NativePointerSource)continue;int b=button.Key;uint msg=b==0?0x202u:b==1?0x205u:b==2?0x208u:0x20Cu;MousePost(button.Value,msg,b>=3?(uint)((b-2)<<16):0,Coords(LastX,LastY));}HeldButtons.Clear();
  foreach(int key in new List<int>(HeldKeys)){IntPtr target;Post(HeldKeyTargets.TryGetValue(key,out target)?target:KeyTarget,IsAlt(key)?0x105u:0x101u,(uint)key,KeyBits(key,true,true));}HeldKeys.Clear();HeldKeyTargets.Clear();KeyTarget=IntPtr.Zero;
  if(ValidTarget(LogicalFocus))Post(LogicalFocus,0x8,0,IntPtr.Zero);LogicalFocus=IntPtr.Zero;ClickButton=-1;
 }
 static int Number(string text,int min,int max){int value;if(!Int32.TryParse(text,out value)||value<min||value>max)throw new ArgumentException("Input limit");return value;}
 static string Line(){StringBuilder line=new StringBuilder(96);int ch;while((ch=Console.In.Read())!=-1){if(ch=='\n')return line.ToString().TrimEnd('\r');if(line.Length>=48000)throw new ArgumentException("Input command too long");line.Append((char)ch);}return line.Length==0?null:line.ToString();}
 static readonly object CommandLock=new object();static readonly LinkedList<string> InputCommands=new LinkedList<string>();
 static bool PointerCommand(string line){return line.Length>1&&line[1]=='\t'&&(line[0]=='M'||line[0]=='I');}
 static void QueueInput(string line){lock(CommandLock){
  // Coalesce before dispatch: the pipe can already contain hundreds of obsolete mouse positions.
  if(PointerCommand(line)&&InputCommands.Last!=null&&PointerCommand(InputCommands.Last.Value))InputCommands.Last.Value=line;
  else{while(InputCommands.Count>=256&&!Quitting)Monitor.Wait(CommandLock);if(Quitting)return;InputCommands.AddLast(line);}
  Monitor.PulseAll(CommandLock);
 }}
 static void ReadInput(){try{string line;while(!Quitting&&(line=Line())!=null){if(line=="Q")break;QueueInput(line);}}catch{}finally{QueueInput("Q");}}
 static string NextInput(){lock(CommandLock){while(InputCommands.Count==0&&!Quitting)Monitor.Wait(CommandLock);if(Quitting)return null;string line=InputCommands.First.Value;InputCommands.RemoveFirst();Monitor.PulseAll(CommandLock);return line;}}
 static void InputLoop(){Dpi();Thread reader=new Thread(ReadInput);reader.IsBackground=true;reader.Name="projection-input-reader";reader.Start();try{while(!Quitting){string line=NextInput();if(line==null)break;lock(InputLock){if(line=="Q"){Release();Quitting=true;RestorePlacement();break;}if(line=="R"){
     // Release queued gestures and held input before accepting another control session.
     Release();Packet(103,0,0,0,0,null);continue;}if(!AllowInput||State()!=0){Release();Packet(102,0,0,0,0,null);continue;}if(!GameForeground()){Release();Packet(106,0,0,0,0,null);continue;}
   try{string[] p=line.Split('\t');bool sent=false;
    if(p[0]=="P"&&p.Length==1){
     Release();NativeGameInput=true;
     if(!MagInitialize())throw new ArgumentException("Cursor hiding unavailable");
     if(!MagShowSystemCursor(false)){MagUninitialize();throw new ArgumentException("Cursor hiding failed");}
     CursorHidden=true;Packet(108,0,0,0,0,null);continue;
    }
    if(NativeGameInput){Packet(102,0,0,0,0,null);continue;}
    if(p[0]=="F"&&p.Length==1){PublishCaret();continue;}
    if(p[0]=="I"&&p.Length==3){IntPtr ime=GetPropW(GetForegroundWindow(),"kwrg_projection_ime");RECT bounds;
     if(ime==IntPtr.Zero||!ImeWindow(ime)||!IsWindowVisible(ime)||!GetClientRect(ime,out bounds))throw new ArgumentException("IME closed");
     int x=Number(p[1],0,MaxEdge-1),y=Number(p[2],0,MaxEdge-1);if(x>=bounds.R||y>=bounds.B)throw new ArgumentException("IME point");
     NativeArea=null;SyncVirtualCursor(ime,x,y);POINT imePoint=new POINT(x,y);if(ClientToScreen(ime,ref imePoint))MoveNativePointer(imePoint);RECT imeBounds;if(GetWindowRect(ime,out imeBounds))ImeArea=new PointerArea{Window=ime,Bounds=imeBounds};Packet(100,0,0,0,0,null);continue;
    }
    if(p[0]=="M"&&p.Length==3||p[0]=="B"&&p.Length==5||p[0]=="W"&&p.Length==5){int offset=p[0]=="M"?1:3;int x=Number(p[offset],-MaxEdge,MaxEdge-1),y=Number(p[offset+1],-MaxEdge,MaxEdge-1);POINT point=new POINT(x,y);IntPtr target=HeldButtons.Count>0?FirstButton():Target(ref point);if(target==IntPtr.Zero){NativeArea=null;Packet(100,0,0,0,0,null);continue;}if(HeldButtons.Count>0){POINT screen=new POINT(x,y);ClientToScreen(Selected,ref screen);ScreenToClient(target,ref screen);point=screen;}LastX=point.X;LastY=point.Y;
     if(p[0]=="M"){
      ImeArea=null;
      if(HoverTarget!=target){if(HoverTarget!=IntPtr.Zero)Post(HoverTarget,0x2A3,0,IntPtr.Zero);HoverTarget=target;}
      IntPtr root=GetAncestor(target,2);
      if(HeldButtons.Count==0&&DirectButtons==0){NativePointerSource=WindowClass(Selected)=="CabinetWClass"||IsOwnedWindow(root)||WindowClass(root)=="Chrome_WidgetWin_1"&&!ChromiumInputHost(target);if(!NativePointerSource)NativeArea=null;}
      if(ControlRelay()!=IntPtr.Zero)PreventSourceActivation();POINT local=new POINT(x,y);ClientToScreen(Selected,ref local);ScreenToClient(root,ref local);SyncVirtualCursor(root,local.X,local.Y);
      sent=NativePointerSource?RefreshNativePointer(x,y):MousePost(target,0x200,Flags(),Coords(point.X,point.Y));
     }
     else if(p[0]=="B"){int b=Number(p[1],0,4);bool down=Number(p[2],0,1)==1;uint message=b==0?(down?0x201u:0x202u):b==1?(down?0x204u:0x205u):b==2?(down?0x207u:0x208u):(down?0x20Bu:0x20Cu);
      // Deliver the exact final move while the previous button state is still
      // held. Some drag handlers need it before the subsequent button-up.
      sent=NativePointerSource||MousePost(target,0x200,Flags(),Coords(point.X,point.Y));
          if(down){CheatProgramWindowScroll.Clear();Interlocked.Increment(ref CaretRevision);if(b==0){CaretClickWindow=GetAncestor(target,2);CaretClick=new POINT(x,y);CaretClickKnown=ClientToScreen(Selected,ref CaretClick)&&ScreenToClient(CaretClickWindow,ref CaretClick);}FocusView(target);HeldButtons[b]=target;KeyTarget=target;long now=DateTime.UtcNow.Ticks;
       if(b==ClickButton&&target==ClickTarget&&Math.Abs(point.X-ClickX)<=4&&Math.Abs(point.Y-ClickY)<=4&&(now-ClickAt)/TimeSpan.TicksPerMillisecond<=GetDoubleClickTime()){message=b==0?0x203u:b==1?0x206u:b==2?0x209u:0x20Du;ClickButton=-1;}
       else{ClickButton=b;ClickTarget=target;ClickX=point.X;ClickY=point.Y;ClickAt=now;}
      }else{IntPtr held;if(HeldButtons.TryGetValue(b,out held))target=held;HeldButtons.Remove(b);}uint flags=Flags();if(b>=3)flags|=(uint)((b-2)<<16);if(!NativePointerSource)sent&=MousePost(target,message,flags,Coords(point.X,point.Y));}
     else{int sx=Number(p[1],-12000,12000),sy=Number(p[2],-12000,12000);POINT screen=new POINT(x,y);ClientToScreen(Selected,ref screen);
       // Scroll providers smooth fractional offsets; native wheel fallback is
       // handled by the same queue for ordinary controls without a scroll pattern.
       // Neither path moves the real cursor or changes foreground focus.
       uint targetPid;GetWindowThreadProcessId(GetAncestor(target,2),out targetPid);bool queued=CheatProgramWindowScroll.Queue(GetAncestor(target,2),(int)targetPid,target,sx,sy,screen.X,screen.Y,Flags(),
        delegate{return !Quitting&&AllowInput&&State()==0&&GameForeground();},delegate(bool ok){if(!Quitting)Packet(ok?100:GameForeground()?105:106,0,0,0,0,null);});
       if(!queued)Packet(105,0,0,0,0,null);continue;
     }
    }else if(p[0]=="K"&&p.Length==4){int vk=Number(p[1],1,255);bool down=Number(p[2],0,1)==1,repeat=Number(p[3],0,1)==1;if(down){Interlocked.Increment(ref CaretRevision);CheatProgramWindowScroll.Clear();}IntPtr heldTarget;KeyTarget=HeldKeyTargets.TryGetValue(vk,out heldTarget)?heldTarget:KeyboardTarget();FocusView(KeyTarget);bool alt=IsAlt(vk);IntPtr bits=KeyBits(vk,!down,repeat||HeldKeys.Contains(vk));if(down){HeldKeys.Add(vk);HeldKeyTargets[vk]=KeyTarget;}else{HeldKeys.Remove(vk);HeldKeyTargets.Remove(vk);}sent=Post(KeyTarget,alt?(down?0x104u:0x105u):(down?0x100u:0x101u),(uint)vk,bits);
    }else if(p[0]=="C"&&p.Length==2){int ch=Number(p[1],32,65535);Interlocked.Increment(ref CaretRevision);CheatProgramWindowScroll.Clear();KeyTarget=KeyboardTarget();FocusView(KeyTarget);sent=Post(KeyTarget,0x102,(uint)ch,new IntPtr(1));}
    else if(p[0]=="T"&&p.Length==2){byte[] utf8=Convert.FromBase64String(p[1]);string text=new UTF8Encoding(false,true).GetString(utf8);if(text.Length>8192)throw new ArgumentException("Text limit");Interlocked.Increment(ref CaretRevision);CheatProgramWindowScroll.Clear();KeyTarget=KeyboardTarget();FocusView(KeyTarget);sent=true;foreach(char ch in text){if(ch=='\r'||ch=='\n'||ch=='\t'||ch>=32&&ch!=127)sent&=Post(KeyTarget,0x102,(uint)(ch=='\n'?'\r':ch),new IntPtr(1));}}
    else throw new ArgumentException("Unknown input");Packet(sent?100:101,0,0,0,0,null);
   }catch(ArgumentException){Release();Packet(102,0,0,0,0,null);}
   }}}catch{}finally{lock(InputLock){Release();Quitting=true;RestorePlacement();if(MouseTimer!=IntPtr.Zero){CloseHandle(MouseTimer);MouseTimer=IntPtr.Zero;}}}}
 static IntPtr FirstButton(){foreach(IntPtr value in HeldButtons.Values)return value;return Selected;}
 static volatile bool CursorHidden;static bool ControlClipped;static RECT ControlClip;
 static readonly MouseProcedure MouseCallback=RelayMouse;
 static readonly ManualResetEvent MouseReady=new ManualResetEvent(false);
 static uint MouseThreadId;static int MouseStartupError,ImeMouseButtons,InterceptedButtons;static bool MouseStarted;static Thread MouseThread;
 static IntPtr RelayMouse(int code,IntPtr message,IntPtr data){
  if(code>=0&&unchecked((uint)Marshal.ReadIntPtr(data,24).ToInt64())==PointerMarker)return CallNextHookEx(IntPtr.Zero,code,message,data);
  int kind=message.ToInt32();
  if(code>=0&&!Quitting&&kind==0x200&&InterceptedButtons!=0&&ControlRelay()!=IntPtr.Zero)return new IntPtr(1);
  if(code>=0&&!Quitting&&kind>=0x201&&kind<=0x20E){
   IntPtr relay=ControlRelay();
   if(relay!=IntPtr.Zero){
    // The entry down reached Windows before control began: its real up must reach Windows too.
    if(kind==0x205&&GetPropW(GetForegroundWindow(),"kwrg_projection_entry_release")!=IntPtr.Zero)return CallNextHookEx(IntPtr.Zero,code,message,data);
    int detail=Marshal.ReadInt32(data,8);bool wheel=kind==0x20A||kind==0x20E;
    int button=wheel?0:kind<=0x203?0:kind<=0x206?1:kind<=0x209?2:(detail>>16)+2;
    bool up=kind==0x202||kind==0x205||kind==0x208||kind==0x20C;
    if(!wheel&&up&&(ImeMouseButtons&(1<<button))!=0){ImeMouseButtons&=~(1<<button);return CallNextHookEx(IntPtr.Zero,code,message,data);}
    POINT at=(POINT)Marshal.PtrToStructure(data,typeof(POINT));PointerArea native=NativeArea;
    // Hit testing can send messages into the thread waiting for this hook; use the validated pointer area instead.
    if(!wheel&&((DirectButtons&(1<<button))!=0||NativePointerReady&&(!up||(InterceptedButtons&(1<<button))==0)&&native!=null&&native.Contains(at)&&IsWindow(native.Window))){
     if(PostMessageW(relay,0x8001,new UIntPtr((uint)kind),new IntPtr(detail))){if(up)DirectButtons&=~(1<<button);else{DirectButtons|=1<<button;InterceptedButtons&=~(1<<button);}return CallNextHookEx(IntPtr.Zero,code,message,data);}
    }
    if(!wheel&&up&&(InterceptedButtons&(1<<button))==0)return CallNextHookEx(IntPtr.Zero,code,message,data);
    IntPtr ime=GetPropW(GetForegroundWindow(),"kwrg_projection_ime");
    if(ime!=IntPtr.Zero&&IsWindowVisible(ime)){
     PointerArea area=ImeArea;
     if(area!=null&&area.Window==ime&&area.Contains(at)){if(!wheel&&!up)ImeMouseButtons|=1<<button;return CallNextHookEx(IntPtr.Zero,code,message,data);}
    }
    // Never call Java or send synchronous window messages from a global input hook.
    if(PostMessageW(relay,0x8001,new UIntPtr((uint)kind),new IntPtr(detail))){if(!wheel){if(up)InterceptedButtons&=~(1<<button);else InterceptedButtons|=1<<button;}return new IntPtr(1);}
   }
   ImeMouseButtons=InterceptedButtons=DirectButtons=0;
  }
  return CallNextHookEx(IntPtr.Zero,code,message,data);
 }
 static void StartMouseRelay(){
  MouseThread=new Thread(delegate(){
   Dpi();MouseThreadId=GetCurrentThreadId();MESSAGE message;PeekMessageW(out message,IntPtr.Zero,0,0,0);
   IntPtr hook=SetWindowsHookExW(14,MouseCallback,GetModuleHandleW(null),0);
   MouseStarted=hook!=IntPtr.Zero;if(!MouseStarted)MouseStartupError=Marshal.GetLastWin32Error();MouseReady.Set();
   try{if(hook!=IntPtr.Zero)while(GetMessageW(out message,IntPtr.Zero,0,0)>0){TranslateMessage(ref message);DispatchMessageW(ref message);}}
   finally{if(hook!=IntPtr.Zero)UnhookWindowsHookEx(hook);}
  });MouseThread.IsBackground=true;MouseThread.Name="projection-mouse-relay";MouseThread.Start();
  if(!MouseReady.WaitOne(2000)||!MouseStarted)throw new IOException("Native mouse relay unavailable: "+MouseStartupError);
 }
 static void StopMouseRelay(){if(MouseThreadId!=0)PostThreadMessageW(MouseThreadId,0x12,UIntPtr.Zero,IntPtr.Zero);if(MouseThread!=null)MouseThread.Join(250);}
 static IntPtr ControlRelay(){
  IntPtr game=GetForegroundWindow();uint pid;GetWindowThreadProcessId(game,out pid);
  if(pid!=OwnerPid)return IntPtr.Zero;IntPtr relay=FindWindowExW(game,IntPtr.Zero,"STATIC","kwrg_projection_mouse");
  uint relayPid;return relay!=IntPtr.Zero&&GetWindowThreadProcessId(relay,out relayPid)!=0&&relayPid==OwnerPid?relay:IntPtr.Zero;
 }
 static void RestoreControlCursor(){lock(InputLock){if(!CursorHidden)return;CursorHidden=false;MagShowSystemCursor(true);MagUninitialize();
  if(ControlClipped&&!GameForeground()){RECT current;if(GetClipCursor(out current)&&current.L==ControlClip.L&&current.T==ControlClip.T&&current.R==ControlClip.R&&current.B==ControlClip.B)UnclipCursor(IntPtr.Zero);}ControlClipped=false;
 }}
 static void SyncVirtualCursor(IntPtr source,int x,int y){
  IntPtr relay=ControlRelay();if(relay==IntPtr.Zero){RestoreControlCursor();return;}
  if(GetPropW(GetForegroundWindow(),"kwrg_projection_entry_release")!=IntPtr.Zero)return;
  if(!CursorHidden){if(!MagInitialize())throw new ArgumentException("Cursor hiding unavailable");if(!MagShowSystemCursor(false)){MagUninitialize();throw new ArgumentException("Cursor hiding failed");}CursorHidden=true;}
  POINT screen=new POINT(x,y);if(!ClientToScreen(source,ref screen))return;
  RECT client;GetClientRect(source,out client);POINT origin=new POINT(0,0);ClientToScreen(source,ref origin);
  RECT clip=new RECT{L=origin.X,T=origin.Y,R=origin.X+client.R,B=origin.Y+client.B};
  if(source!=Selected&&IsOwnedWindow(source)){RECT outer;if(GetWindowRect(source,out outer))clip=outer;}
  if(IsWindow(relay)&&GameForeground()){
   if(ClipCursor(ref clip)){ControlClipped=true;ControlClip=clip;}
   // A warp emits an extra WM_MOUSEMOVE without our held-button flags and cancels text selection.
   if(HeldButtons.Count==0&&!NativePointerSource&&source==Selected)SetCursorPos(screen.X,screen.Y);
  }
 }
 static void Gdi(int edge,int maxPixels,int fps,bool native,long owner,int retryAfterMillis){CurrentBackend=ImeCapture?3:1;Stopwatch fallback=Stopwatch.StartNew();while(!Quitting&&OwnerAlive(owner)&&(retryAfterMillis<=0||fallback.ElapsedMilliseconds<retryAfterMillis)){
  Interlocked.Exchange(ref lastActivity,Environment.TickCount);KeepSourceActive();Stopwatch tick=Stopwatch.StartNew();int state=State();if(state!=0){Packet(state,0,0,0,0,null);if(state==3)break;Thread.Sleep(400);continue;}RECT rect;GetClientRect(Selected,out rect);int sw=rect.R,sh=rect.B,w,h;Dimensions(sw,sh,edge,maxPixels,native,out w,out h);
  IntPtr screen=IntPtr.Zero,source=IntPtr.Zero,target=IntPtr.Zero,srcBmp=IntPtr.Zero,dstBmp=IntPtr.Zero,oldSrc=IntPtr.Zero,oldDst=IntPtr.Zero;
  try{screen=GetDC(IntPtr.Zero);source=CreateCompatibleDC(screen);srcBmp=CreateCompatibleBitmap(screen,sw,sh);if(screen==IntPtr.Zero||source==IntPtr.Zero||srcBmp==IntPtr.Zero)throw new IOException("GDI allocation");oldSrc=SelectObject(source,srcBmp);
   if(!PrintWindow(Selected,source,3)){Packet(7,0,0,0,0,null);Thread.Sleep(250);continue;}
   IntPtr bitmap=srcBmp,dc=source;if(w!=sw||h!=sh){target=CreateCompatibleDC(screen);dstBmp=CreateCompatibleBitmap(screen,w,h);if(target==IntPtr.Zero||dstBmp==IntPtr.Zero)throw new IOException("GDI resize allocation");oldDst=SelectObject(target,dstBmp);SetStretchBltMode(target,4);if(!StretchBlt(target,0,0,w,h,source,0,0,sw,sh,0x00CC0020))throw new IOException("GDI resize");SelectObject(target,oldDst);oldDst=IntPtr.Zero;bitmap=dstBmp;dc=target;}else{SelectObject(source,oldSrc);oldSrc=IntPtr.Zero;}
   byte[] pixels=new byte[checked(w*h*4)];BI info=new BI();info.Size=(uint)Marshal.SizeOf(typeof(BI));info.Width=w;info.Height=-h;info.Planes=1;info.Bits=32;
   if(GetDIBits(dc,bitmap,0,(uint)h,pixels,ref info,0)==0)throw new IOException("GDI pixels");
   bool black=true;for(int i=0;i<pixels.Length;i+=4){pixels[i+3]=255;if(pixels[i]!=0||pixels[i+1]!=0||pixels[i+2]!=0)black=false;}
   if(ImeCapture){ReadyFrame frame=CropIme(new ReadyFrame{Pixels=pixels,W=w,H=h,CW=sw,CH=sh});Packet(1,frame.W,frame.H,frame.CW,frame.CH,frame.Pixels,0,frame.OriginX,frame.OriginY);}
   else Packet(black?8:1,w,h,sw,sh,pixels);
  }finally{if(oldSrc!=IntPtr.Zero)SelectObject(source,oldSrc);if(oldDst!=IntPtr.Zero)SelectObject(target,oldDst);if(srcBmp!=IntPtr.Zero)DeleteObject(srcBmp);if(dstBmp!=IntPtr.Zero)DeleteObject(dstBmp);if(source!=IntPtr.Zero)DeleteDC(source);if(target!=IntPtr.Zero)DeleteDC(target);if(screen!=IntPtr.Zero)ReleaseDC(IntPtr.Zero,screen);}
  Thread.Sleep(Math.Max(1,Delay(w,h,fps)-(int)tick.ElapsedMilliseconds));
 }}
 sealed class ReadyFrame {public byte[] Pixels;public int W,H,CW,CH,OriginX,OriginY,ScreenX,ScreenY,WindowX,WindowY;public long Captured;}
  static ReadyFrame CropIme(ReadyFrame frame){
   int left=frame.W,top=frame.H,right=0,bottom=0;
   for(int y=0;y<frame.H;y++)for(int x=0;x<frame.W;x++){int p=(y*frame.W+x)*4;byte alpha=frame.Pixels[p+3];
    // PrintWindow leaves black padding around the system popup.
    if(alpha!=0&&(alpha!=255||frame.Pixels[p]!=0||frame.Pixels[p+1]!=0||frame.Pixels[p+2]!=0)){left=Math.Min(left,x);right=Math.Max(right,x+1);top=Math.Min(top,y);bottom=Math.Max(bottom,y+1);}
   }
   if(right==0){ReturnPixels(frame.Pixels);frame.Pixels=RentPixels(16);Array.Clear(frame.Pixels,0,16);frame.W=frame.H=frame.CW=frame.CH=2;return frame;}
   left=Math.Min(left,frame.W-2);top=Math.Min(top,frame.H-2);right=Math.Max(right,left+2);bottom=Math.Max(bottom,top+2);
   int width=right-left,height=bottom-top;byte[] pixels=RentPixels(checked(width*height*4));
   for(int y=0;y<height;y++){
    Buffer.BlockCopy(frame.Pixels,((top+y)*frame.W+left)*4,pixels,y*width*4,width*4);
    // Remove only the outside padding, retaining black glyphs inside the original UI.
    for(int x=0;x<width;x++){int p=(y*width+x)*4;if(pixels[p]!=0||pixels[p+1]!=0||pixels[p+2]!=0)break;pixels[p+3]=0;}
    for(int x=width-1;x>=0;x--){int p=(y*width+x)*4;if(pixels[p]!=0||pixels[p+1]!=0||pixels[p+2]!=0)break;pixels[p+3]=0;}
   }
   frame.OriginX=left*frame.CW/frame.W;frame.OriginY=top*frame.CH/frame.H;
   frame.CW=Math.Max(1,width*frame.CW/frame.W);frame.CH=Math.Max(1,height*frame.CH/frame.H);
   ReturnPixels(frame.Pixels);frame.Pixels=pixels;frame.W=width;frame.H=height;return frame;
  }
 sealed class Wgc:IDisposable {
  readonly IntPtr window;readonly bool wholeWindow;volatile bool stopping;
  public string Stage="initialize";IntPtr device,context;readonly IntPtr[] staging=new IntPtr[3];readonly CaptureReadbackQueue readback=new CaptureReadbackQueue(3);readonly int[] outWidths=new int[3],outHeights=new int[3],clientWidths=new int[3],clientHeights=new int[3];IDirect3DDevice projected;Direct3D11CaptureFramePool pool;GraphicsCaptureSession session;GraphicsCaptureItem item;AutoResetEvent arrived=new AutoResetEvent(false);int stagingW,stagingH;SizeInt32 poolSize;
  readonly long[] captureTimes=new long[3];readonly int[] originXs=new int[3],originYs=new int[3],screenXs=new int[3],screenYs=new int[3],windowXs=new int[3],windowYs=new int[3];
  public void Stop(){stopping=true;try{arrived.Set();}catch(ObjectDisposedException){}}
  void ResetStaging(int width,int height){for(int i=0;i<staging.Length;i++)if(staging[i]!=IntPtr.Zero){Marshal.Release(staging[i]);staging[i]=IntPtr.Zero;}readback.Reset();stagingW=width;stagingH=height;}
  ReadyFrame DrainReadback(){
   ReadyFrame ready=null;
   readback.Drain(delegate(int slot,bool publish){
    MAPPED mapped;Stage="readback-map";int result=Method<Map>(context,14)(context,staging[slot],0,1,0x100000,out mapped);
    if(result==unchecked((int)0x887A000A))return false;Marshal.ThrowExceptionForHR(result);
    byte[] pixels=null;
    try{if(publish){int stride=checked(stagingW*4),length=checked(stride*stagingH);pixels=RentPixels(length);
      if(mapped.RowPitch==stride)Marshal.Copy(mapped.Data,pixels,0,length);
      else{if(mapped.RowPitch<stride)throw new IOException("Invalid readback stride");for(int row=0;row<stagingH;row++)Marshal.Copy(new IntPtr(mapped.Data.ToInt64()+row*(long)mapped.RowPitch),pixels,row*stride,stride);}
      byte[] scaled=outWidths[slot]==stagingW&&outHeights[slot]==stagingH?pixels:Resize(pixels,stagingW,stagingH,outWidths[slot],outHeights[slot]);if(scaled!=pixels)ReturnPixels(pixels);pixels=null;
      ready=new ReadyFrame{Pixels=scaled,W=outWidths[slot],H=outHeights[slot],CW=clientWidths[slot],CH=clientHeights[slot],Captured=captureTimes[slot],OriginX=originXs[slot],OriginY=originYs[slot],ScreenX=screenXs[slot],ScreenY=screenYs[slot],WindowX=windowXs[slot],WindowY=windowYs[slot]};
    }}finally{if(pixels!=null)ReturnPixels(pixels);Method<Unmap>(context,15)(context,staging[slot],0);}
    return true;
   });return ready;
  }
  public Wgc():this(Selected,false){}
  public Wgc(IntPtr target,bool whole){window=target;wholeWindow=whole;try{Stage="is-supported";if(!GraphicsCaptureSession.IsSupported())throw new NotSupportedException("WGC unavailable");int level;Stage="create-device";Marshal.ThrowExceptionForHR(D3D11CreateDevice(IntPtr.Zero,1,IntPtr.Zero,0x20,IntPtr.Zero,0,7,out device,out level,out context));
   Stage="project-device";IntPtr dxgi=IntPtr.Zero,inspectable=IntPtr.Zero;Guid dxgiId=new Guid("54EC77FA-1377-44E6-8C32-88FD5F44C84C");try{Marshal.ThrowExceptionForHR(Marshal.QueryInterface(device,ref dxgiId,out dxgi));Marshal.ThrowExceptionForHR(CreateDirect3D11DeviceFromDXGIDevice(dxgi,out inspectable));projected=(IDirect3DDevice)Marshal.GetObjectForIUnknown(inspectable);}finally{if(dxgi!=IntPtr.Zero)Marshal.Release(dxgi);if(inspectable!=IntPtr.Zero)Marshal.Release(inspectable);}
   Stage="capture-item-factory";ItemInterop factory=(ItemInterop)WindowsRuntimeMarshal.GetActivationFactory(typeof(GraphicsCaptureItem));Guid itemId=new Guid("79C3F95B-31F7-4EC2-A464-632EF5D30760");Stage=SelectedMonitor?"create-monitor-item":"create-window-item";IntPtr pointer=SelectedMonitor?factory.CreateForMonitor(window,ref itemId):factory.CreateForWindow(window,ref itemId);try{item=(GraphicsCaptureItem)Marshal.GetObjectForIUnknown(pointer);}finally{Marshal.Release(pointer);}
   Stage="allocate-framepool";poolSize=item.Size;
   if(poolSize.Width<2||poolSize.Height<2||poolSize.Width>MaxEdge+128||poolSize.Height>MaxEdge+128)throw new IOException("WGC dimensions "+poolSize.Width+"x"+poolSize.Height);pool=Direct3D11CaptureFramePool.CreateFreeThreaded(projected,DirectXPixelFormat.B8G8R8A8UIntNormalized,2,poolSize);
   Stage="framepool-event";pool.FrameArrived+=delegate(Direct3D11CaptureFramePool sender,object args){try{arrived.Set();}catch(ObjectDisposedException){}};Stage="create-session";session=pool.CreateCaptureSession(item);ConfigureSession(false);Stage="start-session";session.StartCapture();}catch(Exception failure){try{failure.Data["CaptureStage"]=Stage;}catch{}try{Dispose();}catch{}throw;}
  }
  void ConfigureSession(bool ignored){if(Windows.Foundation.Metadata.ApiInformation.IsPropertyPresent("Windows.Graphics.Capture.GraphicsCaptureSession","IsCursorCaptureEnabled"))session.IsCursorCaptureEnabled=false;}
  public void Run(int edge,int maxPixels,int fps,bool native,long owner,Action<ReadyFrame> publish=null){CurrentBackend=2;long lastGood=Environment.TickCount;bool gotFrame=false;bool suspended=false;
   WaitHandle[] wake=!wholeWindow&&Composition!=null?new WaitHandle[]{arrived,Composition.Changed}:new WaitHandle[]{arrived};
   while(!Quitting&&!stopping&&OwnerAlive(owner)){
   if(wholeWindow){uint affinity;if(!IsWindowVisible(window)||!IsOwnedWindow(window)||GetWindowDisplayAffinity(window,out affinity)&&affinity!=0)return;}
   else{Interlocked.Exchange(ref lastActivity,Environment.TickCount);if(Composition!=null)Composition.Refresh();KeepSourceActive();int state=State();if(state!=0){Packet(state,0,0,0,0,null);if(state==3)return;suspended=true;ResetStaging(0,0);Thread.Sleep(400);continue;}}
   // Rebuild only after an actual lifecycle suspension. A paused/static image
   // need not emit FrameArrived; recreating it periodically caused black flashes.
   if(suspended){SizeInt32 resumed=item.Size;if(resumed.Width>=2&&resumed.Height>=2&&resumed.Width<=MaxEdge+128&&resumed.Height<=MaxEdge+128){Stage="resume-framepool";pool.Recreate(projected,DirectXPixelFormat.B8G8R8A8UIntNormalized,2,resumed);poolSize=resumed;}suspended=false;}
   Stopwatch tick=Stopwatch.StartNew();WaitHandle.WaitAny(wake,readback.Pending?1:100);if(Quitting||!OwnerAlive(owner))return;
   if(!wholeWindow&&Composition!=null)Composition.Refresh();
   ReadyFrame completed=DrainReadback();if(completed!=null){if(publish!=null)publish(completed);else if(Composition!=null)Composition.Parent(completed);else Packet(1,completed.W,completed.H,completed.CW,completed.CH,completed.Pixels,completed.Captured,completed.OriginX,completed.OriginY);gotFrame=true;lastGood=Environment.TickCount;}
   Direct3D11CaptureFrame latest=null,next;SizeInt32 size=default(SizeInt32);int w=2,h=2;
   try{Stage="next-frame";for(int queued=0;queued<2&&(next=pool.TryGetNextFrame())!=null;queued++){Direct3D11CaptureFrame previous=latest;latest=next;if(previous!=null)previous.Dispose();}
   if(latest==null){if(!wholeWindow&&!readback.Pending&&completed==null)Packet(12,0,0,0,0,null);long quiet=(long)(uint)(Environment.TickCount-(int)lastGood);if(!gotFrame&&quiet>15000)throw new IOException("WGC no first frame");continue;}
   size=latest.ContentSize;if(size.Width<2||size.Height<2)continue;
    int left=0,top=0,sw,sh,cw,ch,sourceX=0,sourceY=0,screenX=0,screenY=0,windowX=0,windowY=0;
    if(SelectedMonitor){MONITORINFOEX info;if(!MonitorInfo(out info)){Packet(3,0,0,0,0,null);return;}cw=info.Monitor.R-info.Monitor.L;ch=info.Monitor.B-info.Monitor.T;sw=Math.Min(cw,size.Width);sh=Math.Min(ch,size.Height);}
    else{RECT client,bounds;GetClientRect(window,out client);if(DwmBounds(window,9,out bounds,Marshal.SizeOf(typeof(RECT)))!=0)GetWindowRect(window,out bounds);RECT outer;GetWindowRect(window,out outer);
     windowX=outer.L;windowY=outer.T;
     if(Math.Abs(outer.R-outer.L-size.Width)+Math.Abs(outer.B-outer.T-size.Height)<Math.Abs(bounds.R-bounds.L-size.Width)+Math.Abs(bounds.B-bounds.T-size.Height))bounds=outer;
     POINT origin=new POINT(0,0);ClientToScreen(window,ref origin);
     if(wholeWindow){sw=size.Width;sh=size.Height;screenX=bounds.L;screenY=bounds.T;}
     else{left=Math.Max(0,origin.X-bounds.L);top=Math.Max(0,origin.Y-bounds.T);sourceX=Math.Max(0,bounds.L-origin.X);sourceY=Math.Max(0,bounds.T-origin.Y);sw=Math.Min(client.R-sourceX,size.Width-left);sh=Math.Min(client.B-sourceY,size.Height-top);screenX=origin.X+sourceX;screenY=origin.Y+sourceY;}cw=sw;ch=sh;}
    if(sw<2||sh<2||sw>MaxEdge||sh>MaxEdge||(long)sw*sh>MaxPixels){if(!wholeWindow)Packet(6,0,0,0,0,null);continue;}
    Dimensions(sw,sh,edge,maxPixels,native,out w,out h);IntPtr surface=IntPtr.Zero;Guid textureId=new Guid("6F15AAF2-D208-4E89-9AB4-489535D34F9C");Stage="surface-interface";((DxgiAccess)latest.Surface).GetInterface(ref textureId,out surface);
    try{Stage="surface-copy";DESC desc;Method<GetDesc>(surface,10)(surface,out desc);if(left+sw>desc.Width||top+sh>desc.Height){if(!wholeWindow)Packet(6,0,0,0,0,null);continue;}
     if(stagingW!=sw||stagingH!=sh)ResetStaging(sw,sh);int slot=readback.FreeSlot();if(slot>=0){
      if(staging[slot]==IntPtr.Zero){DESC target=new DESC();target.Width=(uint)sw;target.Height=(uint)sh;target.MipLevels=1;target.ArraySize=1;target.Format=87;target.SampleCount=1;target.Usage=3;target.CPUAccessFlags=0x20000;Marshal.ThrowExceptionForHR(Method<CreateTexture>(device,5)(device,ref target,IntPtr.Zero,out staging[slot]));}
      BOX region=new BOX();region.L=(uint)left;region.T=(uint)top;region.R=(uint)(left+sw);region.B=(uint)(top+sh);region.Bk=1;Method<CopyRegion>(context,46)(context,staging[slot],0,0,0,0,surface,0,ref region);
      outWidths[slot]=w;outHeights[slot]=h;clientWidths[slot]=cw;clientHeights[slot]=ch;
      originXs[slot]=sourceX;originYs[slot]=sourceY;
      screenXs[slot]=screenX;screenYs[slot]=screenY;
      windowXs[slot]=windowX;windowYs[slot]=windowY;
      captureTimes[slot]=CaptureFrameClock.EpochMillis((DateTime.UtcNow.Ticks-621355968000000000L)/10000,Stopwatch.GetTimestamp(),Stopwatch.Frequency,latest.SystemRelativeTime.Ticks);
      readback.Enqueue(slot);Method<FlushContext>(context,111)(context);
     }
    }finally{if(surface!=IntPtr.Zero)Marshal.Release(surface);}
    
   }finally{if(latest!=null)latest.Dispose();if(size.Width>=2&&size.Height>=2&&(size.Width!=poolSize.Width||size.Height!=poolSize.Height)){Stage="resize-framepool";pool.Recreate(projected,DirectXPixelFormat.B8G8R8A8UIntNormalized,2,size);poolSize=size;}}
   // Native surfaces and the frame are released before any transport work.
   if(fps>0){int remaining=Delay(w,h,fps)-(int)tick.ElapsedMilliseconds;if(remaining>0)Thread.Sleep(remaining);}
  }}
  public void Dispose(){if(session!=null)session.Dispose();if(pool!=null)pool.Dispose();if(projected!=null)projected.Dispose();ResetStaging(0,0);if(context!=IntPtr.Zero)Marshal.Release(context);if(device!=IntPtr.Zero)Marshal.Release(device);arrived.Dispose();}
 }
 static unsafe byte[] Resize(byte[] source,int sw,int sh,int w,int h){
  if(sw<=0||sh<=0||w<=0||h<=0||source.LongLength!=(long)sw*sh*4)throw new ArgumentException("Invalid resize dimensions");
  byte[] target=RentPixels(checked(w*h*4));int[] left=new int[w],right=new int[w],weight=new int[w];
  for(int x=0;x<w;x++){double sx=(x+.5)*sw/w-.5;int x0=Math.Max(0,Math.Min(sw-1,(int)Math.Floor(sx)));
   left[x]=x0;right[x]=Math.Min(sw-1,x0+1);weight[x]=(int)Math.Round(Math.Max(0,sx-x0)*256);}
  fixed(byte* sourceBytes=source,targetBytes=target){
   uint* pixels=(uint*)sourceBytes,output=(uint*)targetBytes;
   for(int y=0;y<h;y++){double sy=(y+.5)*sh/h-.5;int y0=Math.Max(0,Math.Min(sh-1,(int)Math.Floor(sy)));
    int row0=y0*sw,row1=Math.Min(sh-1,y0+1)*sw;uint fy=(uint)Math.Round(Math.Max(0,sy-y0)*256),iy=256-fy;
    // Packed red/blue channels can be interpolated together without crossing their 16-bit lanes.
    for(int x=0;x<w;x++){uint fx=(uint)weight[x],ix=256-fx,p00=pixels[row0+left[x]],p10=pixels[row0+right[x]],p01=pixels[row1+left[x]],p11=pixels[row1+right[x]];
     uint a=(((p00&0xFF00FF)*ix+(p10&0xFF00FF)*fx+0x800080)>>8)&0xFF00FF;
     uint b=(((p01&0xFF00FF)*ix+(p11&0xFF00FF)*fx+0x800080)>>8)&0xFF00FF;
     uint rb=((a*iy+b*fy+0x800080)>>8)&0xFF00FF;
     a=(((p00&0xFF00)*ix+(p10&0xFF00)*fx+0x8000)>>8)&0xFF00;
     b=(((p01&0xFF00)*ix+(p11&0xFF00)*fx+0x8000)>>8)&0xFF00;
     uint alpha=(((p00>>24)*ix+(p10>>24)*fx)*iy+((p01>>24)*ix+(p11>>24)*fx)*fy+32768)>>16;
     output[y*w+x]=(alpha<<24)|rb|(((a*iy+b*fy+0x8000)>>8)&0xFF00);
    }
   }
  }return target;
 }
 public static int Main(string[] args){try{Console.SetError(new StreamWriter(Console.OpenStandardError(),new UTF8Encoding(false)){AutoFlush=true});Dpi();if(args.Length==2&&args[0]=="list"){List(Int64.Parse(args[1]));return 0;}if((args.Length!=10&&args.Length!=12&&args.Length!=14)||args[0]!="capture")return 2;
  Selected=new IntPtr(Int64.Parse(args[1]));SelectedPid=Int32.Parse(args[2]);int edge=Math.Max(320,Math.Min(MaxEdge,Int32.Parse(args[3]))),fps=Math.Max(0,Math.Min(120,Int32.Parse(args[4])));long owner=Int64.Parse(args[5]);OwnerPid=checked((int)owner);bool native=args[6]=="1";int pixels=Math.Max(1048576,Math.Min(MaxPixels,Int32.Parse(args[7])));SelectedMonitor=args.Length>=12&&args[10]=="monitor";ImeCapture=args.Length>=12&&args[10]=="ime";if(args.Length>=12){if(args[10]!="monitor"&&args[10]!="window"&&args[10]!="ime")return 3;SelectedSourceId=new UTF8Encoding(false,true).GetString(Convert.FromBase64String(args[11]));}AllowInput=args[8]=="1"&&!SelectedMonitor&&!ImeCapture;string backend=args[9];if(SelectedMonitor?(Selected==IntPtr.Zero||SelectedPid!=0||SelectedSourceId.Length==0):(Selected==IntPtr.Zero||SelectedPid<=0||SelectedPid==owner&&!(ImeCapture&&ImeWindow(Selected))||SelectedPid==Process.GetCurrentProcess().Id))return 3;
  if(ImeCapture&&!ImeWindow(Selected))return 3;
  InitializeSource();
   Writer=new BinaryWriter(Console.OpenStandardOutput());StartOutput();Interlocked.Exchange(ref lastActivity,Environment.TickCount);Timer guardian=new Timer(delegate(object ignored){Dpi();long age=(long)(uint)(Environment.TickCount-(int)Interlocked.Read(ref lastActivity));if(CursorHidden&&!NativeGameInput&&ControlRelay()==IntPtr.Zero)RestoreControlCursor();lock(InputLock){IntPtr foreground=GetForegroundWindow();if(NativeGameInput&&foreground!=IntPtr.Zero&&!GameForeground()&&!ValidTarget(foreground)){Release();Packet(109,0,0,0,0,null);}PublishCaret();if(NoActivateWindow!=IntPtr.Zero&&(!GameForeground()||ControlRelay()==IntPtr.Zero&&!IsIconic(Selected)))RestoreSourceActivation();}if(!OwnerAlive(owner)||age>15000){Quitting=true;if(Composition!=null)Composition.Dispose();RestoreSourceActivation();RestoreControlCursor();RestorePlacement();Environment.Exit(0);}},null,100,100);
  Thread input=new Thread(InputLoop);input.IsBackground=true;input.Start();try{
   if(AllowInput)StartMouseRelay();KeepSourceActive();if(State()==3){Packet(3,0,0,0,0,null);return 0;}
   // Immersive Pinyin popups expose no WGC frames; PrintWindow captures their original UI.
   if(ImeCapture||backend=="gdi"&&!SelectedMonitor){Gdi(edge,pixels,ImeCapture?30:fps,native,owner,0);return 0;}
   if(!SelectedMonitor)Composition=new OwnedComposition(edge,pixels,fps,native,owner);
   int failures=0;while(!Quitting&&OwnerAlive(owner)&&State()!=3){
    Wgc capture=null;try{KeepSourceActive();int state=State();if(state!=0){Packet(state,0,0,0,0,null);Thread.Sleep(100);continue;}capture=new Wgc();capture.Run(edge,pixels,fps,native,owner);return 0;}
    catch(Exception failure){if(Quitting||!OwnerAlive(owner))break;Console.Error.WriteLine("WGC: "+failure.GetType().Name+" (0x"+failure.HResult.ToString("X8")+") stage="+(failure.Data.Contains("CaptureStage")?failure.Data["CaptureStage"]:capture==null?"initialize":capture.Stage));Packet(13,0,0,0,0,null);}
    finally{if(capture!=null)try{capture.Dispose();}catch{}}
    if(Quitting||!OwnerAlive(owner)||State()==3)break;
    // PrintWindow cannot keep accelerated video live. A transient WGC failure must not
    // replace a good compositor frame with a cached GDI screenshot and switch back again.
    failures=Math.Min(5,failures+1);Thread.Sleep(Math.Min(1500,100*(1<<(failures-1))));
    Interlocked.Exchange(ref lastActivity,Environment.TickCount);
   }return 0;
   }finally{Quitting=true;guardian.Dispose();if(Composition!=null)Composition.Dispose();StopMouseRelay();RestoreSourceActivation();RestoreControlCursor();StopOutput();RestorePlacement();foreach(IntPtr process in FollowProcesses.Values)CloseHandle(process);}
 }catch(Exception error){try{Console.Error.WriteLine(error.GetType().Name+": "+error.Message);}catch{}return 1;}}
}
