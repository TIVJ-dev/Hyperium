package cc.hyperium.launch.deobf;

import cc.hyperium.launch.patching.PatchManager;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

public final class DeobfRemapper extends Remapper {

  public static final DeobfRemapper INSTANCE;

  static {
    try {
      INSTANCE = new DeobfRemapper();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  // BiMap<obfName, deobfName>
  private final BiMap<String, String> classNames = HashBiMap.create();
  // Map<obfOwner, Map<obfName / obfId, deobfName>
  private final Map<String, Map<String, String>> fieldNames = Maps.newHashMap();
  private final Map<String, Map<String, String>> methodNames = Maps.newHashMap();
  private final List<String> supertypeMapped = Lists.newArrayList();

  private DeobfRemapper() throws IOException {
    // Load mappings
    try (InputStream joinedSrg = getClass().getResourceAsStream("/mappings/notch-mcp.srg")) {
      try (BufferedReader bis = new BufferedReader(new InputStreamReader(joinedSrg))) {
        String line;
        while ((line = bis.readLine()) != null) {
          String[] parts = line.split(" ");
          switch (parts[0]) {
            case "CL:": {
              classNames.put(parts[1], parts[2]);
              break;
            }
            case "MD:": {
              String obfNameAndOwner = parts[1];
              String obfOwner = obfNameAndOwner.substring(0, obfNameAndOwner.lastIndexOf('/'));
              String obfName = obfNameAndOwner.substring(obfNameAndOwner.lastIndexOf('/') + 1);
              String obfDesc = parts[2];
              String obfId = obfName + obfDesc;
              String deobfNameAndOwner = parts[3];
              String deobfName = deobfNameAndOwner
                  .substring(deobfNameAndOwner.lastIndexOf('/') + 1);
              // Map<obfOwner, Map<obfName / obfId, deobfName>

              methodNames.computeIfAbsent(obfOwner, f -> Maps.newHashMap())
                  .put(obfId, deobfName);

              break;
            }
            case "FD:": {
              String obfNameAndOwner = parts[1];
              String obfOwner = obfNameAndOwner.substring(0, obfNameAndOwner.lastIndexOf('/'));
              String obfName = obfNameAndOwner.substring(obfNameAndOwner.lastIndexOf('/') + 1);

              String deobfNameAndOwner = parts[2];
              String deobfName = deobfNameAndOwner
                  .substring(deobfNameAndOwner.lastIndexOf('/') + 1);
              fieldNames.computeIfAbsent(obfOwner, f -> Maps.newHashMap())
                  .put(obfName, deobfName);
              break;
            }
          }
        }
      }
    }
  }

  @Override
  public String mapFieldName(String owner, String name, String desc) {
    if (classNames.isEmpty()) {
      return name;
    }
    return mapFieldName(owner, name, fieldNames);
  }

  private String mapFieldName(String owner, String name, Map<String, Map<String, String>> data) {
    Map<String, String> fieldNames = getNames(data, owner);
    if (fieldNames == null || fieldNames.isEmpty()) {
      return name;
    }
    return fieldNames.getOrDefault(name, name);
  }

  private Map<String, String> getNames(Map<String, Map<String, String>> map, String owner) {
    if (classNames.containsKey(owner)) {
      findMergeSuperMaps(owner);
    } else {
      findMergeSuperMaps(unmap(owner));
    }
    Map<String, String> result = map.get(owner);
    if (result != null) {
      return result;
    }
    return map.get(unmap(owner));
  }

  public String unmap(String deobf) {
    deobf = deobf.replace('.', '/');
    if (classNames.isEmpty()) {
      return deobf;
    }
    if (classNames.containsValue(deobf)) {
      return classNames.inverse().get(deobf);
    }
    // if it's a subclass of a class that does have mappings, we want to unmap that class
    int dollarIndex = deobf.lastIndexOf('$');
    if (dollarIndex > -1) {
      return unmap(deobf.substring(0, dollarIndex)) + "$" + deobf.substring(dollarIndex + 1);
    }
    return deobf;
  }

  @Override
  public String map(String typeName) {
    typeName = typeName.replace('.', '/');
    if (classNames.isEmpty()) {
      return typeName;
    }
    if (classNames.containsKey(typeName)) {
      return classNames.get(typeName);
    }
    // if it's a subclass of a class that does have mappings, we want to map that class
    int dollarIndex = typeName.lastIndexOf('$');
    if (dollarIndex > -1) {
      return map(typeName.substring(0, dollarIndex)) + "$" + typeName.substring(dollarIndex + 1);
    }
    return typeName;
  }

  @Override
  public String mapMethodName(String owner, String name, String desc) {
    if (classNames.isEmpty()) {
      return name;
    }
    return mapMethodName(owner, name, desc, methodNames);
  }

  private String mapMethodName(String owner, String name, String desc,
      Map<String, Map<String, String>> data) {
    Map<String, String> methodNames = getNames(data, owner);
    if (methodNames == null || methodNames.isEmpty()) {
      return name;
    }
    String key = name + desc;
    return methodNames.getOrDefault(key, name);
  }

  @Override
  public String mapSignature(String signature, boolean typeSignature) {
    if (signature != null && signature.contains("!*")) {
      return null;
    }
    return super.mapSignature(signature, typeSignature);
  }

  void addSupertypeMappings(String name, String superName, String[] interfaces) {
    if (superName == null || superName.isEmpty()) {
      return;
    }
    String[] parents = ArrayUtils.add(interfaces, superName);
    for (String s : parents) {
      findMergeSuperMaps(s);
    }
    supertypeMapped.add(name);
    Map<String, String> srgMethods = methodNames.getOrDefault(name, Maps.newHashMap());
    Map<String, String> srgFields = fieldNames.getOrDefault(name, Maps.newHashMap());
    for (String s : parents) {
      if (methodNames.containsKey(s)) {
        srgMethods.putAll(methodNames.get(s));
      }
      if (fieldNames.containsKey(s)) {
        srgFields.putAll(fieldNames.get(s));
      }
    }
    methodNames.put(name, srgMethods);
    fieldNames.put(name, srgFields);
  }

  private void findMergeSuperMaps(String name) {
    if (supertypeMapped.contains(name)) {
      return;
    }
    try {
      String supr = null;
      String[] interfaces = new String[0];
      byte[] originalData = Launch.classLoader.getClassBytes(name);
      byte[] bytes = PatchManager.INSTANCE.patch(name, originalData, false);
      if (bytes != null) {
        ClassReader cr = new ClassReader(bytes);
        supr = cr.getSuperName();
        interfaces = cr.getInterfaces();
      }
      addSupertypeMappings(name, supr, interfaces);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
// 🦀