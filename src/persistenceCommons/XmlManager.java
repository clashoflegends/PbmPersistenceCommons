/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package persistenceCommons;

import com.thoughtworks.xstream.XStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Singleton para gerenciar o xml que deve ser único.
 *
 * @author gurgel
 */
public class XmlManager implements Serializable {

    private static final Log log = LogFactory.getLog(XmlManager.class);
    private static final BundleManager label = SettingsManager.getInstance().getBundleManager();
    private static XmlManager instance;

    private XmlManager() {
    }

    public synchronized static XmlManager getInstance() {
        if (instance == null) {
            log.debug("Criou instancia do XmlManager.");
            instance = new XmlManager();
        }
        return instance;
    }

    public Object get(File inFile) throws PersistenceException {
        String inFileName = inFile.getName();
        log.debug("Abrindo arquivo: " + inFile.getName());
        InputStream is = null;
        InputStreamReader reader = null;
        try {
            File workFile = null;
            //Verifica se eh o XML ou o Gzip(egf)
            if (getExtension(inFileName).equalsIgnoreCase("egf")) {
                workFile = ZipManager.getInstance().doUncompressGzip(inFile);
            } else {
                workFile = inFile;
            }

            is = new BufferedInputStream(new FileInputStream(workFile));
            reader = new InputStreamReader(is, "UTF-8");
            XStream xstream = new XStream();
            xstream.allowTypesByWildcard(new String[]{"model.**"});
            return xstream.fromXML(reader);

//            XStream xstream = new XStream();
//            reader = xstream.createObjectInputStream(new FileInputStream(file));
//            return xstream.fromXML(reader);
        } catch (FileNotFoundException ex) {
            throw new PersistenceException(label.getString("ARQUIVO.NAO.ENCONTRADO") + inFile.getAbsolutePath());
        } catch (UnsupportedEncodingException ex) {
            throw new PersistenceException(label.getString("ARQUIVO.CORROMPIDO") + inFile.getAbsolutePath());
        } catch (PersistenceException e) {
            throw new PersistenceException(e.getMessage());
        } catch (com.thoughtworks.xstream.converters.ConversionException ex) {
            log.error(ex);
            throw new PersistenceException(
                    String.format(
                            label.getString("ARQUIVO.INCOMPATIVEL.PATH"),
                            inFile.getAbsolutePath(),
                            ex.get("path")
                    )
            );
        } catch (Exception e) {
            throw new PersistenceException(e.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                log.error(ex);
            }
        }
    }

    /**
     * Cria o arquivo temporario para gerar o XML Chama o Zip para gerar o arquivo final
     *
     * @param world
     * @param finalFile
     * @throws PersistenceException
     */
    public void save(Object world, File finalFile) throws PersistenceException {
        try {
            log.debug("Gravando XML. File: " + finalFile.getPath());
//        String msgBuild = String.format("commonsBuild=%s", SysApoio.getVersionClash("version_commons"));
            //cria temp file para o XML
            File tempFile;
            //verifica se o properties esta definindo que os arquivos temporarios devem ser apagados ou nao(debug?)
            if (SettingsManager.getInstance().getConfig("tempZipFiles", "1").equals("1")) {
                String tempFileName = finalFile.getName();
                if (getExtension(tempFileName).equalsIgnoreCase("egf")) {
                    tempFileName = getFileName(tempFileName);
                }
                tempFile = File.createTempFile(tempFileName, null);
                tempFile.deleteOnExit();
            } else {
                String tempFileName = finalFile.getAbsolutePath();
                if (getExtension(tempFileName).equalsIgnoreCase("egf")) {
                    tempFileName = getFileName(tempFileName);
                }
                tempFile = new File(tempFileName);
            }
            FileWriter fw = new FileWriter(tempFile);

            //XStream xstream = new XStream(new DomDriver());
            XStream xstream = new XStream();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(outputStream, "UTF-8"); //UTF-8  //"ISO-8859-1"
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
//            writer.write("<!-- " + msgBuild + "-->\n");
            xstream.toXML(world, writer);
            String xml = outputStream.toString("UTF-8");

            fw.write(xml);
            fw.close();
            ZipManager.getInstance().doCompressGzip(tempFile, finalFile);
            log.debug("Saved file:" + finalFile.getAbsolutePath());
        } catch (IOException ex) {
            throw new PersistenceException("Issues with file...", ex);
        } catch (NullPointerException ex) {
            throw new PersistenceException("Path error to save (null on xml)...", ex);
        }
    }

    /**
     * Used to extract and return the extension of a given file.
     *
     * @param f Incoming file to get the extension of
     * @return <code>String</code> representing the extension of the incoming file.
     */
    private static String getExtension(String f) {
        String ext = "";
        int i = f.lastIndexOf('.');

        if (i > 0 && i < f.length() - 1) {
            ext = f.substring(i + 1);
        }
        return ext;
    }

    /**
     * Used to extract the filename without its extension.
     *
     * @param f Incoming file to get the filename
     * @return <code>String</code> representing the filename without its extension.
     */
    private static String getFileName(String f) {
        String fname = "";
        int i = f.lastIndexOf('.');

        if (i > 0 && i < f.length() - 1) {
            fname = f.substring(0, i);
        }
        return fname;
    }

    public String toXml(Object object) {
        XStream xstream = new XStream();
        return xstream.toXML(object);
    }

    public Object fromXml(String xml) {
        try {
            if (xml != null) {
                XStream xstream = new XStream();
                xstream.allowTypesByWildcard(new String[]{"model.**"});
                return xstream.fromXML(xml);
            }
        } catch (com.thoughtworks.xstream.io.StreamException e) {
            //ignore the empty stream, return null
        }
        return null;
    }
}
