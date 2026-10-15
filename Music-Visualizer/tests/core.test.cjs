const {test}=require('node:test');
const assert=require('node:assert/strict');
const c=require('../src/player-core.js');
test('LRC parses repeated timestamps, metadata, fractions and offsets',()=>{
  const lines=c.parseLrc('[ar:Artist]\n[offset:250]\n[00:02.5][00:12.05]Hello\n[00:01]First\n[00:60]invalid');
  assert.deepEqual(lines,[{time:1.25,text:'First'},{time:2.75,text:'Hello'},{time:12.3,text:'Hello'}]);
});
test('lyrics clear at empty timestamps and can be delayed',()=>{
  const lines=c.parseLrc('[00:03]Hello\n[00:05]\n[00:07]Next');
  assert.equal(c.lyricAt(lines,2),-1);assert.equal(c.lyricAt(lines,5),1);
  assert.equal(c.lyricAt(lines,3,1),-1);assert.equal(c.lyricAt(lines,4,1),0);
});
test('sequential stops at end, repeat wraps, shuffle never repeats with multiple tracks',()=>{
  assert.equal(c.nextIndex(3,4,'sequential'),-1);assert.equal(c.nextIndex(3,4,'repeat'),0);
  for(let i=0;i<12;i++)for(let j=0;j<11;j++)assert.notEqual(c.nextIndex(i,12,'shuffle',()=>j/11),i);
  assert.equal(c.nextIndex(0,1,'shuffle'),-1);assert.equal(c.nextIndex(0,0,'repeat'),-1);
});
test('crossfade bounds short tracks and maintains equal power',()=>{
  assert.equal(c.fadeDuration(3,10),1);assert.equal(c.fadeDuration(Infinity,6),0);
  assert.equal(c.fadeDuration(180,6),6);
  for(const x of [0,.25,.5,.75,1]){const [a,b]=c.fadeGains(x);assert.ok(Math.abs(a*a+b*b-1)<1e-10);}
});
test('filenames have conservative artist/title parsing',()=>{
  assert.deepEqual(c.metadata('Artist - Song - Remix.mp3'),{artist:'Artist',title:'Song - Remix'});
  assert.deepEqual(c.metadata('My Song.flac'),{artist:'',title:'My Song'});
});
