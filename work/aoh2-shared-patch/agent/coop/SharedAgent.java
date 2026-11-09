package coop;
import java.lang.instrument.*;
import java.security.*;
import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;

/** In-memory patch only; pinned upstream archive, no redistributed game classes. */
public final class SharedAgent implements Opcodes {
    static final String P="age/of/civilizations2/jakowski/lukasz/mp/", S=P+"SharedSupport", C="L"+P+"MP_Net$Conn;";

    static byte[] brand(ClassLoader loader,String name,byte[] data){
        String root="age/of/civilizations2/jakowski/lukasz/", main=root+"Messages/Gift/R/Menu_Main", ui=P+"MP_UI", b=P+"KreyliBranding";
        String workshop=root+"Z_Other/ST/sUM";
        String browserBar=P+"MP_Browser$Bar";
        if(!Set.of(P+"MP",ui,browserBar,root+"CFG",root+"MenuManager",main,workshop,root+"Menus/Info/Menu_InGame_ProvInfo",root+"Save/SaveGameManager").contains(name))return data;
        ClassReader reader=new ClassReader(data);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected ClassLoader getClassLoader(){return loader;}};
        reader.accept(new ClassVisitor(ASM8,writer){
            public FieldVisitor visitField(int a,String n,String d,String sig,Object value){
                if(name.equals(P+"MP")&&n.equals("MOD_VERSION"))value="1.0";
                return super.visitField(a,n,d,sig,value);
            }
            public MethodVisitor visitMethod(int a,String n,String d,String sig,String[] ex){
                var mv=super.visitMethod(a,n,d,sig,ex);
                if(name.equals(root+"CFG")&&(n.equals("drawVersion_LEFT_BOT")||n.equals("drawVersionLB"))){
                    mv.visitCode();mv.visitInsn(RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
                }
                if(name.equals(ui)&&n.equals("addCredits")){
                    mv.visitCode();mv.visitVarInsn(ALOAD,0);mv.visitVarInsn(ILOAD,1);
                    mv.visitMethodInsn(INVOKESTATIC,b,"addCredits","(Ljava/util/List;I)I",false);
                    mv.visitInsn(IRETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
                }
                if(name.equals(P+"MP")&&n.equals("updateRemoteConfig")){
                    mv.visitCode();mv.visitMethodInsn(INVOKESTATIC,b,"discoverRegions","()V",false);mv.visitInsn(RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
                }
                return new MethodVisitor(ASM8,mv){
                    public void visitCode(){super.visitCode();
                        if(name.equals(browserBar)&&n.equals("actionEL")&&d.equals("(I)V")){
                            super.visitVarInsn(ILOAD,1);
                            Label normal=new Label();super.visitJumpInsn(IFNE,normal);super.visitInsn(RETURN);super.visitLabel(normal);
                        }
                        if(name.equals(main)&&n.equals("actionEL")&&d.equals("(I)V")){super.visitVarInsn(ILOAD,1);super.visitMethodInsn(INVOKESTATIC,b,"openSinglePlayer","(I)Z",false);Label original=new Label();super.visitJumpInsn(IFEQ,original);super.visitInsn(RETURN);super.visitLabel(original);}
                        if(n.equals("clickEndTurn")&&name.endsWith("Menu_InGame_ProvInfo"))super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","beforeReadyClick","()V",false);
                    }
                    public void visitFieldInsn(int opcode,String owner,String field,String descriptor){
                        super.visitFieldInsn(opcode,owner,field,descriptor);
                        // Keep local mods loading, but never call native UGC without Steam.
                        if(name.equals(workshop)&&n.equals("loadSubscribedItems")&&opcode==GETSTATIC&&owner.equals(workshop)&&field.equals("sUQ")&&descriptor.equals("Z")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliSteam","available","()Z",false);
                            super.visitInsn(IAND);
                        }
                    }
                    public void visitLdcInsn(Object v){
                        if(name.equals(root+"CFG")&&n.equals("gLGMP")&&" & LeoC".equals(v))v="";
                        if("2.2".equals(v))v="1.0";
                        if("https://discord.gg/GDTgYj2dJ".equals(v))v="https://discord.gg/zGdxgSvW4";
                        super.visitLdcInsn(v);
                    }
                    public void visitInsn(int opcode){
                        if(name.equals(browserBar)&&n.equals("<init>")&&opcode==RETURN){
                            super.visitVarInsn(ALOAD,0);
                            super.visitMethodInsn(INVOKESTATIC,b,"browserBar","(L"+root+"Menu;)V",false);
                        }
                        if(name.equals(root+"Save/SaveGameManager")&&n.equals("saveGame_16")&&opcode==RETURN)super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","save","()V",false);
                        if(name.equals(root+"MenuManager")&&n.equals("drawMM")&&d.equals("(Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;)V")&&opcode==RETURN){
                            super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,b,"drawFooter",d,false);
                        }
                        if(name.equals(main)&&(n.equals("<init>")||n.equals("updateLang"))&&opcode==RETURN){
                            super.visitVarInsn(ALOAD,0);super.visitMethodInsn(INVOKESTATIC,b,"mainMenu","(Lage/of/civilizations2/jakowski/lukasz/Menu;)V",false);
                        }
                        if(name.equals(P+"MP")&&n.equals("parseRegions")&&opcode==ARETURN){
                            super.visitMethodInsn(INVOKESTATIC,b,"filterRegions","(Ljava/util/List;)Ljava/util/List;",false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        },ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
    public static void premain(String args,Instrumentation instrumentation)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(Path.of("game.jar"))){
            byte[] block=new byte[65536];
            int read;
            while((read=input.read(block))!=-1)digest.update(block,0,read);
        }
        String hash=HexFormat.of().formatHex(digest.digest());
        if(!hash.equals("db144f0ddb7e62de92ad971eb7bc0b9a66b57cabbfe70b9dcda7d2a70204704c"))throw new IllegalStateException("Unsupported upstream archive");
        instrumentation.addTransformer(new ClassFileTransformer(){
            public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] data){
                boolean launcher=name.equals("aoc/kingdoms/lukasz/jakowski/desktop/DesktopLauncher");
                data=brand(loader,name,data);
                if(!launcher&&!name.equals(P+"MP")&&!name.equals(P+"MP_Save")&&!name.equals(P+"MP_UI")&&!name.equals(P+"MP_UI$PlayersTable")&&!name.equals(P+"MP_Net"))return data;
                try{
                    ClassReader reader=new ClassReader(data);
                    ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){
                        @Override protected ClassLoader getClassLoader(){return loader;}
                    };
                    Set<String> found=new HashSet<>();
                    reader.accept(new ClassVisitor(ASM8,writer){
                        public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] ex){
                            boolean command=name.equals(P+"MP")&&method.equals("cmd")&&desc.equals("(Ljava/lang/String;I[Ljava/lang/Object;)Z");
                            boolean hostForm=name.equals(P+"MP_UI")&&method.equals("host")&&desc.equals("()Lage/of/civilizations2/jakowski/lukasz/Menu;");
                            boolean entryForm=name.equals(P+"MP_UI")&&method.equals("entry")&&desc.equals("()Lage/of/civilizations2/jakowski/lukasz/Menu;");
                            if(entryForm)found.add("entry");
                            if(hostForm)found.add("host");
                            if(command)found.add("cmd");
                            var base=super.visitMethod(access,command?"coopOriginalCmd":hostForm?"coopOriginalHost":entryForm?"coopOriginalEntry":method,desc,sig,ex);
                            return new MethodVisitor(ASM8,base){
                                void call(String n,String d){super.visitMethodInsn(INVOKESTATIC,S,n,d,false);}
                                public void visitFieldInsn(int opcode,String owner,String field,String descriptor){
                                    if(name.equals(P+"MP")&&opcode==GETSTATIC&&owner.equals(name)&&field.equals("turnSeconds")
                                            &&Set.of("update","secondsLeft","timerActive").contains(method)){
                                        super.visitInsn(ICONST_0);
                                    }else super.visitFieldInsn(opcode,owner,field,descriptor);
                                }
                                void guard(String n,String d,int...loads){
                                    for(int i=0;i<loads.length;i+=2)super.visitVarInsn(loads[i],loads[i+1]);
                                    call(n,d);Label proceed=new Label();super.visitJumpInsn(IFEQ,proceed);super.visitInsn(RETURN);super.visitLabel(proceed);
                                }
                                public void visitCode(){super.visitCode();
                                    if(name.equals(P+"MP_Net")&&method.equals("writeLine")){found.add(method);super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,P+"KreyliBrowser","advertise","(Ljava/lang/String;)Ljava/lang/String;",false);super.visitVarInsn(ASTORE,1);}
                                    if(name.equals(P+"MP_UI$PlayersTable")&&method.equals("actionEL")){
                                        found.add(method);super.visitVarInsn(ALOAD,0);super.visitVarInsn(ILOAD,1);
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliMenus","inviteAction","(Lage/of/civilizations2/jakowski/lukasz/Menu;I)Z",false);
                                        Label normal=new Label();super.visitJumpInsn(IFEQ,normal);super.visitInsn(RETURN);super.visitLabel(normal);
                                    }
                                    if(name.equals(P+"MP"))switch(method){
                                        case "onEnterInGame"->{super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","prepare","()V",false);}
                                        case "join"->{super.visitMethodInsn(INVOKESTATIC,P+"KreyliIdentity","apply","()V",false);}
                                        case "onHello"->{super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","beforeHello","()V",false);super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,P+"KreyliIdentity","beforeHello","("+C+"[Ljava/lang/Object;)V",false);found.add(method);guard("rejectExtraPlayer","("+C+")Z",ALOAD,0);}
                                        case "update"->{found.add(method);super.visitMethodInsn(INVOKESTATIC,P+"KreyliPings","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliInputRecovery","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSteam","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliLobby","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","tick","()V",false);}
                                        case "civFree"->{
                                            found.add(method);
                                            super.visitVarInsn(ILOAD,0);
                                            super.visitVarInsn(ALOAD,1);
                                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","reservedForOther","(I"+C+")Z",false);
                                            Label available=new Label();super.visitJumpInsn(IFEQ,available);super.visitInsn(ICONST_0);super.visitInsn(IRETURN);super.visitLabel(available);
                                            super.visitVarInsn(ILOAD,0);call("free","(I)I");super.visitInsn(DUP);super.visitInsn(ICONST_M1);Label normal=new Label();super.visitJumpInsn(IF_ICMPEQ,normal);super.visitInsn(IRETURN);super.visitLabel(normal);super.visitInsn(POP);
                                        }
                                        case "pick"->{found.add(method);guard("pick","(I)Z",ILOAD,0);}
                                        case "onClientLeft"->{found.add(method);guard("leave","("+C+")Z",ALOAD,0);}
                                        case "setPlayerLeader"->{found.add(method);guard("keepLeader","(I)Z",ILOAD,0);}
                                        case "onHostMessage"->{found.add(method);guard("message","("+C+"[Ljava/lang/Object;)Z",ALOAD,0,ALOAD,1);}
                                        case "onClientMessage"->{found.add(method);super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,P+"KreyliLobby","clientMessage","([Ljava/lang/Object;)Z",false);Label normal=new Label();super.visitJumpInsn(IFEQ,normal);super.visitInsn(RETURN);super.visitLabel(normal);super.visitVarInsn(ALOAD,1);call("incoming","([Ljava/lang/Object;)V");}
                                        case "syncAfter"->{guard("lightSync","(Ljava/lang/String;)Z",ALOAD,0);}
                                        case "markReady"->{super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","markReady","()Z",false);Label normal=new Label();super.visitJumpInsn(IFEQ,normal);super.visitInsn(RETURN);super.visitLabel(normal);}
                                        case "endTurnPressed"->{found.add(method);guard("cancelReady","()Z");guard("waitBeforeReady","()Z");}
                                        case "endTurn"->{found.add(method);call("canEnd","()Z");Label yes=new Label();super.visitJumpInsn(IFNE,yes);super.visitInsn(RETURN);super.visitLabel(yes);}
                                        case "sendSnap"->{found.add(method);super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);call("sending","("+C+"Ljava/util/concurrent/Future;)V");}
                                    }
                                }
                                public void visitLdcInsn(Object v){
                                    if(launcher&&"Age of History 2: Definitive Edition Multiplayer".equals(v)){v="Kreyli Coop AOH2";found.add("title");}
                                    if(v instanceof String s&&s.equals("AoH2DE-MP-2.2|2.01 Definitive Edition"))v=s+"|kreyli-coop-3";super.visitLdcInsn(v);
                                }
                                public void visitInsn(int opcode){
                                    if(name.equals(P+"MP_Save")&&method.equals("saveToDisk")&&opcode==RETURN)super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","save","()V",false);
                                    if(name.equals(P+"MP_UI")&&method.equals("updateInGame")&&opcode==RETURN)super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","updateUi","()V",false);
                                    if(name.equals(P+"MP_UI")&&method.equals("chatToggleMenu")&&opcode==ARETURN){found.add(method);super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurnHud","attach","(Lage/of/civilizations2/jakowski/lukasz/Menu;)Lage/of/civilizations2/jakowski/lukasz/Menu;",false);}
                                    if(name.equals(P+"MP_Net")&&method.equals("listServers")&&opcode==ARETURN){found.add(method);super.visitMethodInsn(INVOKESTATIC,P+"KreyliBrowser","filter","(Ljava/util/List;)Ljava/util/List;",false);}
                                    if(name.equals(P+"MP_UI")&&method.equals("build")&&opcode==ARETURN){found.add(method);super.visitVarInsn(ALOAD,0);super.visitMethodInsn(INVOKESTATIC,P+"KreyliLobby","menus","([Lage/of/civilizations2/jakowski/lukasz/Menu;Lage/of/civilizations2/jakowski/lukasz/View;)[Lage/of/civilizations2/jakowski/lukasz/Menu;",false);}
                                    if(name.equals(P+"MP_UI$PlayersTable")&&method.equals("<init>")&&opcode==RETURN){found.add(method);super.visitVarInsn(ALOAD,0);super.visitMethodInsn(INVOKESTATIC,P+"KreyliMenus","appendInvite","(Lage/of/civilizations2/jakowski/lukasz/Menu;)V",false);}
                                    if(name.equals(P+"MP")){
                                        if(opcode==RETURN){if(method.equals("update")){super.visitMethodInsn(INVOKESTATIC,P+"KreyliOrderSync","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","tick","()V",false);}if(method.equals("onNewTurn"))super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","newTurn","()V",false);if(method.equals("onEnterInGame"))super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","restoreConnected","()V",false);}
                                        if(method.equals("loadSettings")&&opcode==RETURN){found.add(method);super.visitMethodInsn(INVOKESTATIC,P+"KreyliIdentity","apply","()V",false);}
                                        if(method.equals("t")&&opcode==ARETURN){found.add(method);super.visitVarInsn(ALOAD,0);call("brandedText","(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");}
                                        if(method.equals("startHost")&&opcode==IRETURN){found.add(method);super.visitInsn(DUP);Label skip=new Label();super.visitJumpInsn(IFEQ,skip);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSteam","hostStarted","()V",false);super.visitLabel(skip);}
                                        if(method.equals("onCmd")&&opcode==RETURN){found.add(method);call("afterCommand","()V");}
                                        if(method.equals("assign")&&opcode==RETURN){super.visitVarInsn(ALOAD,0);super.visitVarInsn(ILOAD,1);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","assigned","("+C+"I)V",false);}
                                        if(method.equals("stop")&&opcode==RETURN){found.add(method);call("reset","()V");super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","reset","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSteam","leaveSession","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliLobby","reset","()V",false);}
                                        if(method.equals("loadSnapshot")&&opcode==RETURN){found.add(method);super.visitVarInsn(ALOAD,1);call("loaded","(Ljava/lang/Object;)V");}
                                        if(method.equals("buildSnapshot")&&opcode==ARETURN){found.add(method);call("built","(Ljava/util/concurrent/Future;)Ljava/util/concurrent/Future;");}
                                    }else if(name.equals(P+"MP_Save")&&method.equals("pieces")&&opcode==ARETURN){found.add(method);call("pieces","(Ljava/util/Map;)Ljava/util/Map;");}
                                    super.visitInsn(opcode);
                                }
                            };
                        }
                        public void visitEnd(){
                            if(name.equals(P+"MP")){
                                var m=super.visitMethod(ACC_PUBLIC|ACC_STATIC|ACC_VARARGS,"cmd","(Ljava/lang/String;I[Ljava/lang/Object;)Z",null,null);
                                m.visitCode();m.visitVarInsn(ALOAD,0);m.visitVarInsn(ILOAD,1);m.visitVarInsn(ALOAD,2);m.visitMethodInsn(INVOKESTATIC,S,"command","(Ljava/lang/String;I[Ljava/lang/Object;)Z",false);m.visitInsn(IRETURN);m.visitMaxs(0,0);m.visitEnd();
                            }
                            if(name.equals(P+"MP_UI")){
                                var m=super.visitMethod(ACC_PRIVATE|ACC_STATIC,"host","()Lage/of/civilizations2/jakowski/lukasz/Menu;",null,null);
                                m.visitCode();m.visitMethodInsn(INVOKESTATIC,P+"SharedHostForm","create","()Lage/of/civilizations2/jakowski/lukasz/Menu;",false);m.visitInsn(ARETURN);m.visitMaxs(0,0);m.visitEnd();
                                m=super.visitMethod(ACC_PRIVATE|ACC_STATIC,"entry","()Lage/of/civilizations2/jakowski/lukasz/Menu;",null,null);
                                m.visitCode();m.visitMethodInsn(INVOKESTATIC,P+"KreyliIdentity","entry","()Lage/of/civilizations2/jakowski/lukasz/Menu;",false);m.visitInsn(ARETURN);m.visitMaxs(0,0);m.visitEnd();
                            }
                            super.visitEnd();
                        }
                    },ClassReader.EXPAND_FRAMES);
                    Set<String> required=launcher?Set.of("title"):name.equals(P+"MP")?Set.of("cmd","civFree","pick","onClientLeft","setPlayerLeader","onHostMessage","onClientMessage","endTurnPressed","endTurn","sendSnap","onCmd","stop","loadSnapshot","buildSnapshot","onHello","update","t","startHost","loadSettings"):name.equals(P+"MP_Save")?Set.of("pieces"):name.equals(P+"MP_UI")?Set.of("host","entry","build","chatToggleMenu"):name.equals(P+"MP_Net")?Set.of("writeLine","listServers"):Set.of("<init>","actionEL");
                    if(!found.equals(required))throw new IllegalStateException("Incomplete hooks: "+found);
                    System.out.println("SHARED_PATCH: installed "+name+" hooks="+found.size());return writer.toByteArray();
                }catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(7);return null;}
            }
        });
    }
}











