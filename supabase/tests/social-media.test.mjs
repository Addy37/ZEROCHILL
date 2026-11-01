import fs from 'node:fs';
import {test} from 'node:test';import assert from 'node:assert/strict';
import {validateMedia,readMedia,PHOTO_LIMIT,GIF_LIMIT} from '../functions/social-media-upload/validation.ts';
test('server validates JPEG PNG WebP GIF magic and rejects MIME spoofing or arbitrary files',()=>{
 const fixture=name=>fs.readFileSync(new URL('../../app/src/test/resources/social-media/'+name,import.meta.url));
 const cases=[['image/jpeg',fixture('orientation-6.jpg')],['image/png',fixture('static.png')],['image/webp',fixture('static.webp')],['image/gif',fixture('animated.gif')]];
 for(const [mime,bytes] of cases) {
  assert.equal(validateMedia(new Uint8Array(bytes),mime),mime==='image/gif'?'social-gifs':'social-photos');
  assert.throws(()=>validateMedia(new Uint8Array(bytes),'text/html'));
 }
 for(const text of ['GIF89a','RIFFxxxxWEBP','<svg/>','<html/>','MZprogram','arbitrary file']) assert.throws(()=>validateMedia(new TextEncoder().encode(text),'image/jpeg'));
});
test('server limits static photos to 5 MB and GIFs to 10 MB including streamed uploads',async()=>{
 const photo=new Uint8Array(PHOTO_LIMIT+1);photo.set([255,216,255]);assert.throws(()=>validateMedia(photo,'image/jpeg'),/5 MB/);
 const gif=new Uint8Array(GIF_LIMIT+1);gif.set(new TextEncoder().encode('GIF89a'));assert.throws(()=>validateMedia(gif,'image/gif'),/10 MB/);
 await assert.rejects(()=>readMedia(new Request('https://test.invalid',{method:'POST',body:gif})),/10 MB/);
});
