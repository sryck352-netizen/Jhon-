// JHON: agente automático usando GitHub Models com GITHUB_TOKEN efêmero.
// Entrada: Issue [JHON]. Saída: arquivos permitidos em games/aetheria/.
// Nunca executa shell produzido pelo modelo; o PR requer revisão.
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve("games/aetheria");
const REPO = process.env.GITHUB_REPOSITORY;
const TOKEN = process.env.GITHUB_TOKEN;
const issueNumber = Number(process.env.JHON_ISSUE_NUMBER);
if (!REPO || !TOKEN || !Number.isInteger(issueNumber) || issueNumber < 1) {
  throw Error("Repositório/token/número de issue inválidos.");
}
const headers = { Authorization: `Bearer ${TOKEN}`, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28" };
const response = await fetch(`https://api.github.com/repos/${REPO}/issues/${issueNumber}`, { headers });
if (!response.ok) throw Error(`Não foi possível buscar a Issue: HTTP ${response.status}`);
const issue = await response.json();
if (issue.user?.login !== "sryck352-netizen" || !issue.title?.startsWith("[JHON]")) {
  throw Error("Tarefa não autorizada: apenas Issues [JHON] do proprietário são aceitas.");
}
const files = [];
function discover(dir) {
  if (!fs.existsSync(dir)) return;
  for (const d of fs.readdirSync(dir, {withFileTypes:true})) {
    const p = path.join(dir,d.name);
    if (d.isDirectory()) discover(p);
    else if (d.isFile() && /\.(gd|tscn|godot|md)$/.test(d.name)) {
      const text = fs.readFileSync(p, "utf8");
      files.push({path:path.relative(process.cwd(),p).replaceAll(path.sep,"/"),content:text.slice(0,10000)});
    }
  }
}
discover(ROOT);
const context = files.slice(0,12).map(f=>`# FILE ${f.path}\n${f.content}`).join("\n\n");
const system = `Você é Jhon, engenheiro de jogos especializado em Godot 4.7.2, GDScript e Android.
Você deve produzir uma melhoria REAL pequena e testável no código atual de Aetheria — Ecos da Aurora.
Nóctis é o personagem inicial, câmera 3D terceira pessoa, controles Android; 7 elementos.
Meta final: fantasia 3D cinematográfica. O protótipo tem malhas PROVISÓRIAS; nunca apresentá-las como arte final. Trabalhe em P01: primeiro movimento/câmera, depois NPC Aria, Sentinela e três magias de Sombra.
Respeite história: Vaelorian elegante e humanoide; Oitavo Eco NÃO é elemento, revelação apenas no jogo 2.
NÃO altere scripts do Jhon, documentação fora da pasta do jogo, GitHub Actions nem arquivos secretos.
Retorne SOMENTE um objeto JSON com campos "summary" string e "files" array de 1 a 3 objetos contendo "path" e "content". Arquivos UTF-8 completos, e apenas .gd ou .tscn sob games/aetheria/. Ao modificar arquivo, devolva seu conteúdo COMPLETO, não diff. Priorize 1 arquivo que entregue funcionalidade real. Não escreva explicações fora do JSON.`;
const prompt = `Issue #${issueNumber}: ${issue.title}\n\n${String(issue.body||"").slice(0,6500)}\n\nARQUIVOS ATUAIS:\n${context.slice(0,18000)}\n\nFaça uma alteração concreta de jogo compatível com o código existente. Não finja que testou em Android.`;
const model = process.env.JHON_MODEL || "openai/gpt-4o-mini";
async function generate() {
  const res = await fetch("https://models.github.ai/inference/chat/completions",{
    method:"POST",
    headers: { Authorization:`Bearer ${TOKEN}`, "Content-Type":"application/json", Accept:"application/json"},
    body:JSON.stringify({model,messages:[{role:"system",content:system},{role:"user",content:prompt}],temperature:0.2,max_tokens:3500})
  });
  const raw = await res.text();
  if (!res.ok) throw Error(`GitHub Models falhou (HTTP ${res.status}): ${raw.slice(0,400)}. Verifique cota e permissão models:read.`);
  const parsed = JSON.parse(raw);
  let output = String(parsed.choices?.[0]?.message?.content || "").trim();
  if (output.startsWith("```")) output=output.replace(/^```(?:json)?\s*/,"").replace(/\s*```$/,"");
  let obj;
  try {obj=JSON.parse(output);} catch(e){throw Error(`Resposta da IA não é JSON válido: ${output.slice(0,350)}`);}
  return obj;
}
const result = await generate();
if (!result || !Array.isArray(result.files) || result.files.length<1 || result.files.length>3) throw Error("IA não entregou entre 1 e 3 arquivos.");
let total=0,hasCode=false;
for (const file of result.files) {
  if (!file || typeof file.path!=="string" || typeof file.content!=="string") throw Error("Arquivo inválido.");
  const rel=file.path.replaceAll("\\","/");
  if (!/^games\/aetheria\/(?:scripts|scenes)\/[a-zA-Z0-9_\/-]+\.(?:gd|tscn)$/.test(rel) || rel.includes("..")) throw Error(`Caminho não autorizado: ${rel}`);
  if (file.content.length>35000) throw Error("Arquivo muito grande.");
  total+=file.content.length;
  if (rel.endsWith(".gd")) hasCode=true;
}
if (total>65000) throw Error("Resposta excedeu limite.");
if (!hasCode) throw Error("O resultado deve incluir código GDScript.");
for(const file of result.files) {
  const absolute=path.resolve(file.path);
  if (!absolute.startsWith(ROOT + path.sep)) throw Error("Tentativa de escrever fora do jogo.");
  fs.mkdirSync(path.dirname(absolute),{recursive:true});
  fs.writeFileSync(absolute,file.content,"utf8");
  console.log(`Atualizado: ${file.path}`);
}
fs.writeFileSync("jhon-summary.txt",String(result.summary||"Alteração proposta").slice(0,1000));
console.log("JHON: código recebido de GitHub Models; nenhuma execução de código gerado nesta etapa.");
