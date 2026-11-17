using System;

// WGC's SystemRelativeTime is in 100ns QPC units. Preserve that capture time
// across GPU readback and pipe transport instead of re-dating an old picture.
public static class CaptureFrameClock {
 public static long EpochMillis(long utcNow,long qpcNow,long frequency,long relativeTicks){
  if(frequency<=0||relativeTicks<=0)return utcNow;
  double age=qpcNow*(1000d/frequency)-relativeTicks/10000d;
  if(Double.IsNaN(age)||Double.IsInfinity(age)||age<=0)return utcNow;
  return utcNow-(long)Math.Min(utcNow,Math.Round(age));
 }
}

// Metadata only: at most three GPU copies can be pending. Busy staging resources
// are never synchronously waited on or reused, and obsolete ready frames are freed.
public sealed class CaptureReadbackQueue {
 readonly long[] pending;long serial,displayed;
 public CaptureReadbackQueue(int slots){if(slots<1||slots>8)throw new ArgumentOutOfRangeException("slots");pending=new long[slots];}
 public bool Pending {get{foreach(long value in pending)if(value!=0)return true;return false;}}
 public int FreeSlot(){for(int i=0;i<pending.Length;i++)if(pending[i]==0)return i;return -1;}
 public void Enqueue(int slot){if(slot<0||slot>=pending.Length||pending[slot]!=0)throw new InvalidOperationException("Readback slot unavailable");pending[slot]=++serial;}
 public void Reset(){Array.Clear(pending,0,pending.Length);displayed=serial;}
 // Consume must use a nonblocking map and return false while GPU work is busy.
 // publish=false means that the ready copy is obsolete and needs no CPU copy.
 public bool Drain(Func<int,bool,bool> consume){
  bool published=false;long ceiling=Int64.MaxValue;
  for(int pass=0;pass<pending.Length;pass++){
   int slot=-1;long order=0;for(int i=0;i<pending.Length;i++)if(pending[i]>order&&pending[i]<ceiling){slot=i;order=pending[i];}
   if(slot<0)break;ceiling=order;bool publish=order>displayed;
   if(consume(slot,publish)){pending[slot]=0;if(publish){displayed=order;published=true;}}
  }
  return published;
 }
}
