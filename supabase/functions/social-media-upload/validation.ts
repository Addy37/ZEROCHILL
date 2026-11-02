import {validContainer} from "./structure.ts";
export const PHOTO_LIMIT=5*1024*1024, GIF_LIMIT=10*1024*1024;
export function validateMedia(bytes:Uint8Array, mime:string):string {
 const ascii=(start:number,end:number)=>new TextDecoder('ascii').decode(bytes.subarray(start,end));
 const actual=bytes.length>=6 && /^GIF8[79]a$/.test(ascii(0,6)) ? 'image/gif'
  : bytes.length>=3 && bytes[0]===255 && bytes[1]===216 && bytes[2]===255 ? 'image/jpeg'
  : bytes.length>=8 && [137,80,78,71,13,10,26,10].every((v,i)=>bytes[i]===v) ? 'image/png'
  : bytes.length>=12 && ascii(0,4)==='RIFF' && ascii(8,12)==='WEBP' ? 'image/webp' : '';
 if(!actual || actual!==mime) throw Error('Choose a JPEG, PNG, WebP or GIF image.');
 if(bytes.length>(actual==='image/gif'?GIF_LIMIT:PHOTO_LIMIT)) throw Error(actual==='image/gif'?'This GIF is too large. Maximum size is 10 MB.':'This photo is too large. Maximum upload size is 5 MB.');
 if(!validContainer(bytes,actual)) throw Error('This image is damaged or unsupported. Choose another image.');
 return actual==='image/gif'?'social-gifs':'social-photos';
}
export async function readMedia(request:Request):Promise<Uint8Array> {
 if(Number(request.headers.get('content-length'))>GIF_LIMIT) throw Error('Maximum upload size is 10 MB.');
 const reader=request.body?.getReader();if(!reader) throw Error('Choose an image.');
 const chunks:Uint8Array[]=[];let size=0;
 try { while(true) {
  const {done,value}=await reader.read();if(done) break;
  size+=value.length;if(size>GIF_LIMIT) {await reader.cancel();throw Error('Maximum upload size is 10 MB.');}
  chunks.push(value);
 }} finally {reader.releaseLock();}
 const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks) {bytes.set(chunk,offset);offset+=chunk.length;}
 return bytes;
}
