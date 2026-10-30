package com.onebytelabs.zlplay.remote;

import android.util.Base64;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The QR carries the LAN endpoints and pins TLS automatically, without manual secrets. */
public final class QrInvitation {
 public final String key,certificate;
 public final List<String> hosts;
 private QrInvitation(String key,String certificate,List<String> hosts){this.key=key;this.certificate=certificate;this.hosts=hosts;}
 public static QrInvitation parse(String value)throws Exception {
  if(value==null||value.length()>3000||!value.startsWith("zlplay-remote:"))throw new IllegalArgumentException("QR");
  JSONObject data=new JSONObject(new String(Base64.decode(value.substring(14),Base64.URL_SAFE|Base64.NO_WRAP),StandardCharsets.UTF_8));
  long remaining=data.getLong("expires")-System.currentTimeMillis();
  String key=data.getString("key"),certificate=data.getString("cert");
  if(data.getInt("v")!=1||remaining<=0||remaining>11*60000||!key.matches("[a-f0-9]{64}")||!certificate.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("QR");
  JSONArray addresses=data.getJSONArray("hosts");if(addresses.length()<1||addresses.length()>4)throw new IllegalArgumentException("QR");
  List<String> hosts=new ArrayList<>();
  for(int i=0;i<addresses.length();i++){
   String host=addresses.getString(i);if(!host.matches("[0-9.]{7,15}:[0-9]{1,5}"))throw new IllegalArgumentException("QR");
   int colon=host.lastIndexOf(':'),port=Integer.parseInt(host.substring(colon+1));if(port<1||port>65535)throw new IllegalArgumentException("QR");
   String ip=host.substring(0,colon);String[] octets=ip.split("\\.");if(octets.length!=4)throw new IllegalArgumentException("QR");
   for(String octet:octets)if(Integer.parseInt(octet)>255)throw new IllegalArgumentException("QR");
   if(!(ip.startsWith("192.168.")||ip.startsWith("10.")||ip.startsWith("127.")||ip.startsWith("169.254.")||(octets[0].equals("172")&&Integer.parseInt(octets[1])>=16&&Integer.parseInt(octets[1])<=31)))throw new IllegalArgumentException("QR");
   hosts.add(host);
  }
  return new QrInvitation(key,certificate,Collections.unmodifiableList(hosts));
 }
}
