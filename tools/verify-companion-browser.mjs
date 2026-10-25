import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp,readFile,rm,writeFile} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';

const require=createRequire(import.meta.url);
const root=path.resolve(import.meta.dirname,'..');
const native=path.join(root,'apps/android/app/src/main/assets/companion');
const ios=path.join(root,'apps/ios/Resources/CompanionRemote');
for(const name of ['index.html','remote.js','remote.css'])assert.deepEqual(await readFile(path.join(native,name)),await readFile(path.join(ios,name)),`Platform asset drift: ${name}`);
const protocol=require(path.join(native,'remote.js'));
assert.throws(()=>protocol.encodeRequest('bad','reserve','fixture'));
assert.throws(()=>protocol.encodeRequest(crypto.randomUUID(),'search',''));
assert.throws(()=>protocol.encodeRequest(crypto.randomUUID(),'camera'));
assert.throws(()=>protocol.encodeRequest(crypto.randomUUID(),'search','x'.repeat(129)));
const id=crypto.randomUUID();
const bytes=protocol.encodeRequest(id,'search','{Tala} 🎵','José');
assert.equal(new DataView(bytes.buffer).getUint16(16),6);
assert.throws(()=>protocol.snapshot({request:id,version:1,source:'youtube'},id));
console.log('Companion assets and bounded request contract checks passed.');
const scratch=await mkdtemp(path.join(os.tmpdir(),'kanta-browser-'));
const host=spawn('java',['-cp',process.argv[2]||path.join(root,'apps/android/app/build/core-regression-classes'),'ph.kanta.reborn.core.BrowserRemoteBrowserHost',native,path.join(scratch,'host')],{stdio:['ignore','ignore','pipe']});
let hostError='';host.stderr.on('data',data=>hostError+=data);
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
let browser,ws;
try{
  let config;
  for(let i=0;i<100;i++){try{config=(await readFile(path.join(scratch,'host'),'utf8')).split('\n');break;}catch{if(host.exitCode!==null)throw Error(hostError);await pause(100);}}
  assert.ok(config,'TLS harness did not start');
  const [origin,code]=config;
  const chrome=process.env.KANTA_CHROME||'/usr/bin/google-chrome';
  browser=spawn(chrome,['--headless=new','--no-sandbox','--disable-dev-shm-usage','--disable-gpu','--no-first-run','--no-default-browser-check','--disable-background-networking','--ignore-certificate-errors','--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${path.join(scratch,'chrome')}`,'about:blank'],{stdio:'ignore'});
  let port;
  for(let i=0;i<100;i++){try{port=(await readFile(path.join(scratch,'chrome/DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{if(browser.exitCode!==null)throw Error('Chrome exited');await pause(100);}}
  assert.ok(port,'Chrome DevTools did not start');
  const targets=await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true});});
  let serial=0;const pending=new Map();const browserErrors=[];
  ws.addEventListener('message',event=>{const value=JSON.parse(event.data);if(value.method==='Runtime.exceptionThrown')browserErrors.push(value.params.exceptionDetails.exception?.description?.slice(0,200)||value.params.exceptionDetails.text);if(value.id&&pending.has(value.id)){const callback=pending.get(value.id);pending.delete(value.id);value.error?callback.reject(Error(value.error.message)):callback.resolve(value.result);}});
  const cdp=(method,params={})=>new Promise((resolve,reject)=>{const id=++serial;const deadline=setTimeout(()=>{pending.delete(id);reject(Error(`CDP timeout: ${method}`));},15000);pending.set(id,{resolve:value=>{clearTimeout(deadline);resolve(value);},reject:error=>{clearTimeout(deadline);reject(error);}});ws.send(JSON.stringify({id,method,params}));});
  const evaluate=async expression=>{const result=await cdp('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.exceptionDetails)throw Error(result.exceptionDetails.text+': '+result.exceptionDetails.exception?.description);return result.result.value;};
  const wait=async expression=>{for(let i=0;i<100;i++){if(await evaluate(expression))return;await pause(50);}const diagnostics=await evaluate("JSON.stringify({ready:document.readyState,hidden:document.hidden,status:document.getElementById('status')?.textContent,notice:document.getElementById('notice')?.textContent,valid:document.getElementById('code')?.validity.valid})");throw Error(`Browser condition failed: ${expression}; ${diagnostics}; exceptions=${JSON.stringify(browserErrors)}`);};
  await cdp('Runtime.enable');await cdp('Page.enable');await cdp('Emulation.setFocusEmulationEnabled',{enabled:true});await cdp('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await cdp('Page.navigate',{url:origin+'/'});await cdp('Page.bringToFront');await wait("document.readyState==='complete' && !document.hidden && !!document.getElementById('pair-form')");
  await evaluate(`document.getElementById('code').value=${JSON.stringify(code)};document.getElementById('pair-form').requestSubmit()`);
  await wait("document.getElementById('notice').textContent.includes('fingerprint')");
  assert.equal(await evaluate("document.getElementById('controller').hidden"),true,'Unverified pairing bypassed');
  await evaluate(`document.getElementById('verified').checked=true;document.getElementById('code').value=${JSON.stringify(code)};document.getElementById('pair-form').requestSubmit()`);
  await wait("!document.getElementById('controller').hidden && !document.getElementById('lookup').disabled");
  assert.equal(await evaluate("document.querySelectorAll('[data-digit]').length"),10);
  assert.equal(await evaluate("new Set([...document.querySelectorAll('[data-digit]')].map(b=>b.dataset.digit)).size"),10);
  await evaluate("document.querySelector('[data-digit=\"0\"]').click();document.querySelector('[data-digit=\"0\"]').click();document.querySelector('[data-digit=\"1\"]').click();document.getElementById('search-form').requestSubmit()");
  await wait("document.getElementById('results').textContent.includes('Morning Light') && !document.getElementById('lookup').disabled");
  assert.equal(await evaluate("document.querySelectorAll('#results script').length"),0,'Host metadata became executable HTML');
  await evaluate("document.querySelector('#results button').click()");await wait("document.querySelectorAll('#queue .row').length===1 && !document.getElementById('lookup').disabled");
  await evaluate("document.querySelector('#results button').click()");await wait("document.querySelectorAll('#queue .row').length===2 && !document.getElementById('lookup').disabled");
  await evaluate("document.querySelector('#queue button').click()");await wait("document.querySelectorAll('#queue .row').length===1 && !document.getElementById('lookup').disabled");
  await evaluate("document.getElementById('play-pause').click()");await wait("document.getElementById('transport-state').textContent==='Playing' && !document.getElementById('play-pause').disabled");
  await evaluate("document.getElementById('play-pause').click()");await wait("document.getElementById('transport-state').textContent==='Paused / stopped' && !document.getElementById('play-pause').disabled");
  await evaluate("document.getElementById('stop').click()");await wait("!document.getElementById('stop').disabled");
  assert.equal(await evaluate("document.documentElement.scrollWidth<=window.innerWidth"),true,'Phone layout overflows');
  for(const width of [320,768,1024]){await cdp('Emulation.setDeviceMetricsOverride',{width,height:844,deviceScaleFactor:1,mobile:true});assert.equal(await evaluate("document.documentElement.scrollWidth<=window.innerWidth"),true,`Layout overflow at ${width}`);}
  if(process.env.KANTA_BROWSER_SCREENSHOT){await evaluate("document.getElementById('pairing').hidden=true");const shot=await cdp('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});await writeFile(process.env.KANTA_BROWSER_SCREENSHOT,Buffer.from(shot.data,'base64'));}
  await evaluate("document.getElementById('disconnect').click()");await wait("document.getElementById('controller').hidden && !document.getElementById('pairing').hidden");
  assert.equal(await evaluate("document.getElementById('code').value"),'');
  console.log('Companion browser: TLS pairing, keypad, search, safe metadata, distinct reservations, cancellation, Play/Pause/Stop, disconnect and 320–1024px layouts passed.');
}finally{ws?.close();browser?.kill('SIGKILL');host.kill('SIGKILL');await pause(100);await rm(scratch,{recursive:true,force:true,maxRetries:5,retryDelay:100});}
