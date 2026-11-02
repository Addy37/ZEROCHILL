// Complete-container validation without changing animation or decoding unbounded pixels.
// A container parser is not a malware scanner or a full compressed-pixel decoder.
export function validContainer(b:Uint8Array,mime:string):boolean {
 const view=new DataView(b.buffer,b.byteOffset,b.byteLength);
 const u16=(n:number)=>view.getUint16(n,true),be16=(n:number)=>view.getUint16(n,false);
 const u32=(n:number)=>view.getUint32(n,true),be32=(n:number)=>view.getUint32(n,false);
 const word=(n:number)=>String.fromCharCode(...b.subarray(n,n+4));
 try {
 if(mime==='image/gif') {
  if(b.length<14||!u16(6)||!u16(8))return false;
  let p=13+((b[10]&128)?3*2**((b[10]&7)+1):0),frames=0;
  const blocks=()=>{let data=false;while(p<b.length){const size=b[p++];if(!size)return data;p+=size;if(p>b.length)return false;data=true;}return false;};
  while(p<b.length){const type=b[p++];
   if(type===59)return frames>0&&p===b.length;
   if(type===33){if(p>=b.length)return false;p++;if(!blocks())return false;}
   else if(type===44){
    if(p+9>b.length||!u16(p+4)||!u16(p+6)||u16(p)+u16(p+4)>u16(6)||u16(p+2)+u16(p+6)>u16(8))return false;
    const flags=b[p+8];p+=9;if(flags&128)p+=3*2**((flags&7)+1);
    if(p>=b.length||b[p]<2||b[p]>8)return false;p++;if(!blocks())return false;frames++;
   }else return false;
  }return false;
 }
 if(mime==='image/png') {
  let p=8,header=false,data=false;
  while(p+12<=b.length){const size=be32(p),type=word(p+4),end=p+12+size;if(end>b.length)return false;
   if(!header){if(type!=='IHDR'||size!==13||!be32(p+8)||!be32(p+12)||Math.max(be32(p+8),be32(p+12))>2560)return false;header=true;}
   if(type==='IDAT'&&size>0)data=true;
   if(type==='IEND')return size===0&&data&&end===b.length;p=end;
  }return false;
 }
 if(mime==='image/webp') {
  if(b.length<20||u32(4)!==b.length-8)return false;let p=12,frame=false;
  while(p+8<=b.length){const type=word(p),size=u32(p+4),end=p+8+size;if(end>b.length)return false;
   if(type==='ANIM'||type==='ANMF')return false;
   if(type==='VP8 '){if(size<10||b[p+11]!==157||b[p+12]!==1||b[p+13]!==42)return false;
    const w=u16(p+14)&16383,h=u16(p+16)&16383;if(!w||!h||Math.max(w,h)>2560)return false;frame=true;
   }else if(type==='VP8L'){if(size<5||b[p+8]!==47)return false;const bits=u32(p+9),w=(bits&16383)+1,h=((bits>>>14)&16383)+1;if(Math.max(w,h)>2560)return false;frame=true;}
   p=end+(size&1);
  }return frame&&p===b.length;
 }
 if(mime==='image/jpeg') {
  let p=2,frame=false,scan=false;
  while(p<b.length){if(b[p++]!==255)return false;while(b[p]===255)p++;const marker=b[p++];
   if(marker===217)return frame&&scan&&p===b.length;
   if(marker===0||marker===216||p+2>b.length)return false;
   const length=be16(p);if(length<2||p+length>b.length)return false;
   if([192,193,194].includes(marker)){if(length<8)return false;const h=be16(p+3),w=be16(p+5);if(!w||!h||Math.max(w,h)>2560)return false;frame=true;}
   p+=length;if(marker===218){scan=true;while(p+1<b.length){if(b[p]===255){const next=b[p+1];if(next!==0&&!(next>=208&&next<=215))break;p+=2;}else p++;}}
  }return false;
 }
 }catch{return false;}
 return false;
}
