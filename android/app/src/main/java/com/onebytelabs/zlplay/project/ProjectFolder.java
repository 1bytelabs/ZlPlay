package com.onebytelabs.zlplay.project;
import java.io.*;
import java.nio.charset.*;
import java.text.Normalizer;
import java.util.*;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

/** Read-only inventory of the folder explicitly granted by the user. */
public final class ProjectFolder {
 public String fallbackScope="";public ProjectFolder forSong(String prefix){ProjectFolder scoped=new ProjectFolder(tree);scoped.files.putAll(files);scoped.fallbackScope=prefix+"/";return scoped;}
 public final Uri tree;public final Map<String,Uri> files=new LinkedHashMap<>();
 public static final class Missing extends IOException {public final boolean ambiguous;public Missing(){this(false);}public Missing(boolean ambiguous){super("Missing or ambiguous project audio");this.ambiguous=ambiguous;}}
 public ProjectFolder(Uri tree){this.tree=tree;}
 public static ProjectFolder scan(Context context,Uri tree)throws IOException{
  ProjectFolder result=new ProjectFolder(tree);Set<String> seen=new HashSet<>();int[] count={0};
  result.walk(context,DocumentsContract.getTreeDocumentId(tree),"",0,seen,count);return result;
 }
 private void walk(Context context,String id,String path,int depth,Set<String> seen,int[] count)throws IOException{
  if(depth>16||!seen.add(id))throw new IOException("Folder too deep or cyclic");
  Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
  try(Cursor rows=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
   if(rows==null)throw new IOException("Folder unavailable");while(rows.moveToNext()){
    if(++count[0]>16384)throw new IOException("Folder too large");String name=rows.getString(1),child=rows.getString(0);if(name==null||name.contains("/")||name.equals(".")||name.equals(".."))throw new IOException("Invalid name");
    String relative=path+name;if(DocumentsContract.Document.MIME_TYPE_DIR.equals(rows.getString(2)))walk(context,child,relative+"/",depth+1,seen,count);else files.put(relative,DocumentsContract.buildDocumentUriUsingTree(tree,child));
   }
  }catch(SecurityException denied){throw new IOException("Folder permission unavailable",denied);}
 }
 public List<String> projects(){List<String> list=new ArrayList<>();for(String file:files.keySet())if(file.toLowerCase(Locale.ROOT).endsWith(".zlplay")||file.toLowerCase(Locale.ROOT).endsWith(".rpp"))list.add(file);list.sort(String.CASE_INSENSITIVE_ORDER);return list;}
 public List<String> setlists(){List<String> out=new ArrayList<>();for(String file:files.keySet())if(file.toLowerCase(Locale.ROOT).matches(".*\\.(zlset|zlplaylibrary)$"))out.add(file);out.sort(String.CASE_INSENSITIVE_ORDER);return out;}
 private boolean inScope(String file){return key(file).startsWith(key(fallbackScope));}
 private static String key(String path){return Normalizer.normalize(path,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
 public Uri resolve(String project,String reference)throws IOException{
  if(reference.startsWith("@click:")){String name=reference.substring(7);try{return resolve(project,name+".wav");}catch(Missing missing){if(missing.ambiguous)throw missing;}Uri click=null;for(var entry:files.entrySet()){if(!inScope(entry.getKey()))continue;String base=entry.getKey().substring(entry.getKey().lastIndexOf('/')+1).toLowerCase(Locale.ROOT);if(base.matches(".*(?:click|clique|claqueta|metronom).*\\.(?:wav|mp3)")){if(click!=null)throw new Missing(true);click=entry.getValue();}}if(click==null)throw new Missing();return click;}
  String ref=reference.replace('\\','/');if(ref.startsWith("@click:"))ref=ref.substring(7)+".wav";
  if(ref.contains("://"))throw new Missing();boolean absolute=ref.startsWith("/")||ref.matches("^[A-Za-z]:/.*");
  if(!absolute){String parent=project.contains("/")?project.substring(0,project.lastIndexOf('/')+1):"";Deque<String> parts=new ArrayDeque<>();for(String part:(parent+ref).split("/")){if(part.isEmpty()||part.equals("."))continue;if(part.equals("..")){if(parts.isEmpty())throw new Missing();parts.removeLast();}else parts.add(part);}String wanted=key(String.join("/",parts));Uri found=null;for(var entry:files.entrySet())if(key(entry.getKey()).equals(wanted)){if(found!=null)throw new Missing(true);found=entry.getValue();}if(found!=null)return found;}
  // Relocated desktop paths are accepted only for a unique basename inside this grant.
  String base=key(ref.substring(ref.lastIndexOf('/')+1));Uri found=null;for(var entry:files.entrySet())if(inScope(entry.getKey())&&key(entry.getKey().substring(entry.getKey().lastIndexOf('/')+1)).equals(base)){if(found!=null)throw new Missing();found=entry.getValue();}if(found==null)throw new Missing();return found;
 }
 public String read(Context context,String project)throws IOException{
  Uri uri=files.get(project);if(uri==null)throw new Missing();byte[] bytes;
  try(InputStream input=context.getContentResolver().openInputStream(uri)){if(input==null)throw new Missing();ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=input.read(block))!=-1){if(output.size()+n>4*1024*1024)throw new IOException("Project too large");output.write(block,0,n);}bytes=output.toByteArray();}if(bytes.length>4*1024*1024)throw new IOException("Project too large");
  Charset charset=StandardCharsets.UTF_8;int skip=0;if(bytes.length>=2&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe){charset=StandardCharsets.UTF_16LE;skip=2;}else if(bytes.length>=2&&bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff){charset=StandardCharsets.UTF_16BE;skip=2;}else if(bytes.length>=3&&bytes[0]==(byte)0xef&&bytes[1]==(byte)0xbb&&bytes[2]==(byte)0xbf)skip=3;
  return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes,skip,bytes.length-skip)).toString();
 }
}
