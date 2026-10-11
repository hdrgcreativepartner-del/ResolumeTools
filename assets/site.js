const tools=[
{id:"audio-bridge",title:"HDRG Audio Bridge",kind:"audio",icon:"♫",version:"v1.2",ready:true,description:"Convert MP3/WAV to FLAC and extract FLAC audio from MP4/MOV. Smart drag & drop, batch processing, same-source output.",url:"https://github.com/hdrgcreativepartner-del/ResolumeTools/actions/workflows/audio-bridge-windows.yml"},
{id:"fx-pack",title:"HDRG FX Pack",kind:"fx",icon:"✳",version:"Planned",ready:false,description:"A collection of live visual effects for Resolume Arena 7."},
{id:"slice-tools",title:"HDRG Slice Tools",kind:"utility",icon:"▦",version:"Planned",ready:false,description:"Mapping and slice utilities for LED workflows."}
];
let selected="all";
const cards=document.querySelector("#cards");
function draw(){cards.replaceChildren();for(const t of tools.filter(t=>selected==="all"||t.kind===selected)){const card=document.createElement("article");card.className="card";
const icon=document.createElement("div");icon.className="tileicon";icon.textContent=t.icon;
const tag=document.createElement("span");tag.className="tag"+(t.ready?"":" soon");tag.textContent=t.ready?"SOURCE / BUILD":"COMING SOON";
const h=document.createElement("h3");h.textContent=t.title;
const desc=document.createElement("p");desc.textContent=t.description;
const foot=document.createElement("div");foot.className="foot";
const v=document.createElement("small");v.textContent=t.version;foot.append(v);
if(t.ready){const link=document.createElement("a");link.className="btn primary";link.href=t.url;link.target="_blank";link.rel="noopener";link.textContent="Get Windows Build ↗";link.title="GitHub Actions build page. Requires a successful build artifact; may require GitHub sign-in.";foot.append(link)}else{const txt=document.createElement("span");txt.className="muted";txt.textContent="In development";foot.append(txt)}
card.append(icon,tag,h,desc,foot);cards.append(card)}}
draw();
for(const btn of document.querySelectorAll(".filter"))btn.addEventListener("click",()=>{selected=btn.dataset.category;document.querySelectorAll(".filter").forEach(x=>x.classList.toggle("active",x===btn));draw()});
const dialog=document.querySelector("#admin-dialog");document.querySelector("#login-link").addEventListener("click",e=>{e.preventDefault();dialog.showModal()});document.querySelector("#close-dialog").addEventListener("click",()=>dialog.close());
