# Eclipse Setup Guide for PGP Decryption Project

## Quick Fix for Import Errors

If you're seeing import errors like "The import org.bouncycastle cannot be resolved", follow these steps:

### Option 1: Import as Existing Maven Project (Recommended)

1. **Open Eclipse**
2. **File → Import → Existing Maven Projects**
3. **Browse to the `/workspace` directory**
4. **Select the project and click Finish**
5. **Wait for Maven to download dependencies** (may take a few minutes)
6. **Right-click project → Maven → Reload Projects**

### Option 2: If Maven Integration Doesn't Work

The project has been pre-configured with all necessary JAR files in the `lib/` directory:

1. **Refresh the project** (F5 or right-click → Refresh)
2. **Check that the following JARs are in your classpath:**
   - `lib/bcpg-jdk18on-1.76.jar`
   - `lib/bcprov-jdk18on-1.76.jar`
   - `lib/bcpkix-jdk18on-1.76.jar`
   - `lib/bcutil-jdk18on-1.76.jar`

3. **If JARs are missing from classpath:**
   - Right-click project → Properties
   - Go to "Java Build Path" → "Libraries"
   - Click "Add External JARs"
   - Add all 4 JAR files from the `lib/` directory

### Option 3: Manual Classpath Configuration

If you still have issues:

1. **Right-click project → Properties**
2. **Java Build Path → Libraries**
3. **Remove any broken references**
4. **Add External JARs** and select all 4 Bouncy Castle JARs from `lib/`
5. **Apply and Close**

## Verify Setup

After setup, you should be able to:

1. **Open `PGPDecryptor.java`** - no import errors
2. **Open `PGPDecryptionExample.java`** - no import errors
3. **Build the project** (Ctrl+B) - no compilation errors

## Running the Application

### From Eclipse:
1. **Right-click `PGPDecryptionExample.java`**
2. **Run As → Java Application**

### From Command Line:
```bash
java -cp "target/classes:lib/*" com.example.pgp.PGPDecryptionExample
```

## Troubleshooting

### "Package does not exist" errors:
- Ensure JDK 17 is selected (Project Properties → Java Build Path → Modulepath/Classpath)
- Check that Maven dependencies are properly resolved

### "Cannot resolve symbol" errors:
- Clean and rebuild: Project → Clean → Clean all projects
- Refresh project (F5)

### Maven issues:
- Right-click project → Maven → Reload Projects
- Right-click project → Maven → Update Project (check "Force Update")

## Project Structure
```
/workspace/
├── src/main/java/com/example/pgp/
│   ├── PGPDecryptor.java          (Main decryption class)
│   └── PGPDecryptionExample.java  (Example usage)
├── lib/                           (Bouncy Castle JARs)
├── target/classes/                (Compiled classes)
├── pom.xml                        (Maven configuration)
└── .project, .classpath           (Eclipse configuration)
```

## Ready to Use!

Once setup is complete, you can decrypt PGP files by running the `PGPDecryptionExample` class and following the interactive prompts.