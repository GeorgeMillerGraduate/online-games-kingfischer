from pathlib import Path
import shutil,json
root=Path(__file__).parent
out=root/'preview'
out.mkdir(exist_ok=True)
for name in ['index.html','style.css','js','assets','vendor','CREDITS.html','PORT-STATUS.md','COPYING.txt','NOTICE.md','STOCKFISH-AUTHORS']:
 src=root/name
 if src.is_dir():shutil.copytree(src,out/name,dirs_exist_ok=True)
 elif src.exists():shutil.copy2(src,out/name)
(root/'.openai/hosting.json').write_text(json.dumps({'static':{'directory':'preview'}}))
