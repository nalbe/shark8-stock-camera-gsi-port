import java.io.*;
import java.util.zip.*;
import java.util.Enumeration;

public class Repack {
    public static void main(String[] args) throws Exception {
        File src = new File(args[0]);
        File newClasses = new File(args[1]);
        File bvDex = new File(args[2]);
        File out = new File(args[3]);
        ZipFile zf = new ZipFile(src);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out));
        zos.setLevel(9);
        Enumeration<? extends ZipEntry> en = zf.entries();
        byte[] buf = new byte[1 << 16];
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String name = e.getName();
            if (name.startsWith("META-INF/")) continue;
            if (name.endsWith(".dex")) continue; // replaced
            boolean store = name.matches("resources\\.arsc|lib/.*\\.so") || e.getMethod() == ZipEntry.STORED;
            ZipEntry ne = new ZipEntry(name);
            ne.setMethod(store ? ZipEntry.STORED : ZipEntry.DEFLATED);
            if (store) {
                InputStream in = zf.getInputStream(e);
                long size = e.getSize();
                ne.setSize(size);
                CRC32 crc = new CRC32();
                ByteArrayOutputStream bos = new ByteArrayOutputStream((int) size);
                int n;
                while ((n = in.read(buf)) > 0) { bos.write(buf, 0, n); crc.update(buf, 0, n); }
                in.close();
                ne.setCrc(crc.getValue());
                zos.putNextEntry(ne);
                zos.write(bos.toByteArray());
            } else {
                zos.putNextEntry(ne);
                InputStream in = zf.getInputStream(e);
                int n;
                while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                in.close();
            }
            zos.closeEntry();
        }
        // patched classes.dex, stored
        writeStored(zos, "classes.dex", readAll(newClasses));
        // bvprop classes2.dex, stored
        writeStored(zos, "classes2.dex", readAll(bvDex));

        // extra native libs from addlib/ dir
        File addlib = new File("addlib");
        if (addlib.isDirectory()) {
            for (File f : addlib.listFiles()) {
                if (!f.isDirectory()) continue;
                for (File so : f.listFiles()) {
                    if (!so.getName().endsWith(".so")) continue;
                    byte[] data = readAll(so);
                    writeStored(zos, "lib/" + f.getName() + "/" + so.getName(), data);
                    System.out.println("added lib: " + so.getName() + " (" + data.length + " bytes)");
                }
            }
        }

        zos.close();
        zf.close();
        System.out.println("repacked: " + out.length());
    }

    static void writeStored(ZipOutputStream zos, String name, byte[] data) throws Exception {
        ZipEntry de = new ZipEntry(name);
        de.setMethod(ZipEntry.STORED);
        de.setSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        de.setCrc(crc.getValue());
        zos.putNextEntry(de);
        zos.write(data);
        zos.closeEntry();
    }

    static byte[] readAll(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }
}