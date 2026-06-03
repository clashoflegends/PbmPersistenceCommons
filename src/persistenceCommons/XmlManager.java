/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package persistenceCommons;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.TreeMapConverter;
import com.thoughtworks.xstream.converters.collections.TreeSetConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.io.BufferedInputStream;
import java.util.Comparator;
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
            // XStream's default ReflectionConverter does not call readResolve().
            // Register a replacement at PRIORITY_LOW that does, so fields absent
            // from old EGFs get their defaults after deserialization.
            xstream.registerConverter(
                new com.thoughtworks.xstream.converters.reflection.ReflectionConverter(
                        xstream.getMapper(), xstream.getReflectionProvider()) {
                    @Override
                    public Object unmarshal(HierarchicalStreamReader reader,
                            UnmarshallingContext context) {
                        Object result = super.unmarshal(reader, context);
                        return invokeReadResolve(result);
                    }
                    private Object invokeReadResolve(Object obj) {
                        // Walk the full hierarchy so every readResolve() fires —
                        // a subclass method must not shadow the superclass one.
                        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
                            try {
                                java.lang.reflect.Method m = c.getDeclaredMethod("readResolve");
                                m.setAccessible(true);
                                m.invoke(obj);
                            } catch (NoSuchMethodException e) {
                                // not on this class, continue up
                            } catch (java.lang.reflect.InvocationTargetException
                                    | IllegalAccessException e) {
                                break;
                            }
                        }
                        return obj;
                    }
                }, XStream.PRIORITY_LOW);
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
            // XStream 1.4.21 changed TreeSet's canonical alias from "tree-set" to
            // "sorted-set". The alias is registered against SortedSet (the interface
            // DefaultImplementationsMapper maps TreeSet to), not TreeSet itself.
            xstream.alias("tree-set", java.util.SortedSet.class);
            // XStream 1.4.21 dropped <no-comparator/> for null-comparator TreeMaps/TreeSets.
            // Old Counselor clients expect it; restore the old behaviour.
            xstream.registerConverter(new TreeMapConverter(xstream.getMapper()) {
                @Override
                protected void marshalComparator(Comparator comparator, HierarchicalStreamWriter writer, MarshallingContext context) {
                    if (comparator == null) {
                        writer.startNode("no-comparator");
                        writer.endNode();
                    } else {
                        super.marshalComparator(comparator, writer, context);
                    }
                }
            });
            xstream.registerConverter(new TreeSetConverter(xstream.getMapper()) {
                @Override
                public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
                    java.util.TreeSet<?> treeSet = (java.util.TreeSet<?>) source;
                    if (treeSet.comparator() == null) {
                        writer.startNode("no-comparator");
                        writer.endNode();
                    } else {
                        writer.startNode("comparator");
                        context.convertAnother(treeSet.comparator());
                        writer.endNode();
                    }
                    for (Object item : treeSet) {
                        writeItem(item, context, writer);
                    }
                }
            });

            // Omit cdToken from EGF output — XStream 1.3.1 (Java 8 Counselor) throws
            // CannotResolveClassException on unknown fields. Remove after jpackage distribution.
            try {
                xstream.omitField(Class.forName("model.Nacao"), "cdToken");
            } catch (ClassNotFoundException ignored) {}

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
