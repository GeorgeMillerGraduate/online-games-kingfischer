import fs from 'node:fs';
import assert from 'node:assert/strict';
import {Network} from '../js/engine/Network.js';
import {Evaluator} from '../js/engine/Evaluator.js';
import {Position} from '../js/engine/Position.js';
import {MoveGenerator as G} from '../js/engine/MoveGenerator.js';
import {Move} from '../js/engine/Move.js';
import {Search} from '../js/engine/Search.js';
const bytes=fs.readFileSync(new URL('../networks/nn-1a298aa575a0.nnue',import.meta.url));
const network=new Network(bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength));
const results=[];
for(const fen of[Position.START,'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1']){
 const p=new Position(fen),search=new Search(p,new Evaluator(network));
 const start=performance.now(),r=await search.run({timeMs:600,maxDepth:3});
 assert(G.legal(p).includes(r.bestMove));assert.equal(p.fen(),fen);assert(r.lines.length>0);assert(performance.now()-start<1300);
 for(const line of r.lines){const q=new Position(fen);for(const m of line.pv){assert(G.legal(q).includes(m));q.make(m);}}
 results.push({fen,depth:r.depth,staticPreview:r.staticPreview,nodes:r.nodes,best:Move.uci(r.bestMove),ms:Math.round(performance.now()-start)});
}
fs.writeFileSync(new URL('nnue-search-results.json',import.meta.url),JSON.stringify(results,null,2));console.log(results);
