package de.kneitzel;

import java.net.URLEncoder;

public class Test {
    public static void main(String[] args) throws Exception {
        String config = "dvn=WSDEV0001&lca=192.168.0.69&lcp=4196&wbp=80&wkm=0&lcm=255.255.255.0&lcg=192.168.0.254&dsa=192.168.1.4&dsp=4196&ipm=1&bdr=6&dtb=0&prt=0&stb=0&flc=0&ndr=0&nrt=300&rct=12&ptc=0&rto=0&emh=0&rcg=0&nwk=&rtp=&post=Submit";
        String encodedData = URLEncoder.encode(config, "UTF-8");
        System.out.println(encodedData);
    }
}
