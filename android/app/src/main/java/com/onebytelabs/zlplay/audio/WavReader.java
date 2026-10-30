package com.onebytelabs.zlplay.audio;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Bounded RIFF/WAVE reader: integer PCM 8/16/24/32 and float32, mono/stereo. */
public final class WavReader {
    public static final class Unsupported extends IOException { public Unsupported(){super("Unsupported WAV format");} }
    public static final class TooLarge extends IOException { public TooLarge(){super("Decoded audio budget exceeded");} }
    public static final class Cancelled extends IOException { public Cancelled(){super("Import cancelled");} }
    static final class Format {int encoding,channels,rate,bits,align;}
    static int word(byte[] a,int i){return (a[i]&255)|((a[i+1]&255)<<8);}
    static long uint(byte[] a,int i){return (a[i]&255L)|((a[i+1]&255L)<<8)|((a[i+2]&255L)<<16)|((a[i+3]&255L)<<24);}
    private static byte[] bytes(InputStream in,int size)throws IOException{byte[] result=new byte[size];read(in,result,size);return result;}
    private static void read(InputStream in,byte[] into,int length)throws IOException{int at=0;while(at<length){if(Thread.currentThread().isInterrupted())throw new Cancelled();int n=in.read(into,at,length-at);if(n<0)throw new EOFException("Truncated WAV");if(n==0)continue;at+=n;}}
    private static void skip(InputStream in,long amount)throws IOException{while(amount>0){if(Thread.currentThread().isInterrupted())throw new Cancelled();long n=in.skip(amount);if(n<=0){if(in.read()<0)throw new EOFException("Truncated WAV chunk");n=1;}amount-=n;}}
    static boolean tag(byte[] bytes,int at,String tag){return new String(bytes,at,4,StandardCharsets.US_ASCII).equals(tag);}
    static Format parseFormat(byte[] f)throws IOException {
        int length=f.length;if(length<16)throw new Unsupported();Format format=new Format();
format.encoding=word(f,0);format.channels=word(f,2);format.rate=(int)uint(f,4);format.align=word(f,12);format.bits=word(f,14);
                if(format.encoding==65534){
                    byte[] tail={0,0,16,0,(byte)128,0,0,(byte)170,0,56,(byte)155,113};
                    if(length<40||word(f,16)<22)throw new Unsupported();for(int i=0;i<12;i++)if(f[28+i]!=tail[i])throw new Unsupported();format.encoding=(int)uint(f,24);
                }
                if(format.channels<1||format.channels>2||format.rate<8000||format.rate>192000||format.align!=format.channels*(format.bits/8))throw new Unsupported();
                if(!((format.encoding==1&&(format.bits==8||format.bits==16||format.bits==24||format.bits==32))||(format.encoding==3&&format.bits==32)))throw new Unsupported();
        return format;
    }
    static float pcm(byte[] block,int i,int encoding,int bits)throws IOException {
        float value;int size=bits/8;
        if(encoding==3)value=Float.intBitsToFloat((int)uint(block,i));
        else if(size==1)value=((block[i]&255)-128)/128f;
        else if(size==2)value=(short)word(block,i)/32768f;
        else if(size==3){int v=(block[i]&255)|((block[i+1]&255)<<8)|(block[i+2]<<16);value=v/8388608f;}
        else value=(int)uint(block,i)/2147483648f;
        if(!Float.isFinite(value))throw new Unsupported();return value;
    }
    public static PcmTrack decode(InputStream source,String name,long maxDecodedBytes)throws IOException {
        BufferedInputStream in=new BufferedInputStream(source,32768);byte[] header=bytes(in,12);
        if(!tag(header,0,"RIFF")||!tag(header,8,"WAVE"))throw new Unsupported();
        long remaining=uint(header,4)-4;if(remaining<8)throw new Unsupported();Format format=null;
        while(remaining>=8){byte[] chunk=bytes(in,8);remaining-=8;long length=uint(chunk,4),padded=length+(length&1);if(padded>remaining)throw new EOFException("Invalid RIFF size");
            if(tag(chunk,0,"fmt ")){
                if(length<16||length>4096)throw new Unsupported();byte[] f=bytes(in,(int)length);format=new Format();
                format=parseFormat(f);
            }else if(tag(chunk,0,"data")){
                if(format==null||length==0||length%format.align!=0)throw new Unsupported();
                long count=length/(format.bits/8);if(count>Integer.MAX_VALUE-8||count*4>maxDecodedBytes)throw new TooLarge();
                float[] pcm=new float[(int)count];byte[] block=new byte[8192];int sampleBytes=format.bits/8,at=0;long unread=length;
                while(unread>0){int n=(int)Math.min(unread,block.length-block.length%sampleBytes);read(in,block,n);
                    for(int i=0;i<n;i+=sampleBytes){float value;
                        if(format.encoding==3)value=Float.intBitsToFloat((int)uint(block,i));
                        else if(sampleBytes==1)value=((block[i]&255)-128)/128f;
                        else if(sampleBytes==2)value=(short)word(block,i)/32768f;
                        else if(sampleBytes==3){int v=(block[i]&255)|((block[i+1]&255)<<8)|(block[i+2]<<16);value=v/8388608f;}
                        else value=(int)uint(block,i)/2147483648f;
                        if(!Float.isFinite(value))throw new Unsupported();pcm[at++]=value;
                    }unread-=n;
                }
                return new PcmTrack(name,format.rate,format.channels,pcm);
            }else skip(in,length);
            if((length&1)!=0)skip(in,1);remaining-=padded;
        }throw new Unsupported();
    }
}
