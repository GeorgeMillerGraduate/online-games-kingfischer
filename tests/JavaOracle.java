import com.kingfischer.engine.chess.*;
import com.kingfischer.engine.movegen.*;
import com.kingfischer.engine.nnue.*;
import com.kingfischer.engine.eval.*;
import java.nio.file.*;
import java.util.*;
public class JavaOracle {
 public static void main(String[] args)throws Exception{
  Network n=Network.load(Path.of(args[0]));
  var fens=Files.readAllLines(Path.of(args[1]));
  for(int j=0;j<fens.size();j++){
   String fen=fens.get(j);Position p=new Position(fen);int[] m=new int[256];int count=MoveGenerator.legal(p,m);String[] u=new String[count];for(int i=0;i<count;i++)u[i]=Move.uci(m[i]);Arrays.sort(u);
   String tail="";
   if(j<80&&!p.inCheck()){
    AccumulatorStack a=new AccumulatorStack(n);long raw=a.raw(p);tail="\t"+(int)(raw>>32)+"\t"+(int)raw+"\t"+Evaluation.evaluate(p,a);
   }
   System.out.println(j+"\t"+Long.toUnsignedString(p.key,16)+"\t"+String.join(",",u)+tail);
  }
 }
}
