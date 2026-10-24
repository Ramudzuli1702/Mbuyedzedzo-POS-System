package com.pos.scanner;

/** Only a pairing code with a pinned desktop identity may open a scanner connection. */
final class PairingEndpoint {
    final String host,fingerprint,encoded;final int port;
    private PairingEndpoint(String host,int port,String fingerprint,String encoded){this.host=host;this.port=port;this.fingerprint=fingerprint;this.encoded=encoded;}
    static PairingEndpoint parse(String value) {
        if(value==null)return null;String encoded=value.trim();
        if(!encoded.matches("POS-TLS:[A-Za-z0-9.\\-]+:[0-9]{1,5}:[a-f0-9]{64}"))return null;
        String[] parts=encoded.split(":",-1);
        try{int port=Integer.parseInt(parts[2]);if(port<1 || port>65535)return null;return new PairingEndpoint(parts[1],port,parts[3],encoded);}
        catch(NumberFormatException e){return null;}
    }
    static String display(String value){PairingEndpoint endpoint=parse(value);return endpoint==null?"Scan the desktop QR code":endpoint.host+":"+endpoint.port;}
}
