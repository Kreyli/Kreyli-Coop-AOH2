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
        String quick=P+"KreyliQuickGame", diplomacy=root+"GameManager";
        String browserBar=P+"MP_Browser$Bar";
        String touchManager=root+"TouchManager";
        String touch=root+"Touch",province=root+"Menus/Info/Menu_InGame_ProvInfo",profile=P+"KreyliProfileUi";
        String countryChoice=P+"KreyliCountryChoice", newGame=root+"Menus/NewGame/Menu_CreateNewGame";
        String hud=P+"KreyliTurnHud", readyButton=root+"Button/Game/Button_GameNextTurn";
        String top=root+"Menus/ZRest/Menu_InGame_";
        boolean topMenu=name.equals(top+"1")||name.equals(top+"2");
        String buttonText=root+"Button/ButtonM", startup=root+"Menus/Menu_InitGame";
        String gameAction=root+"GameAction", realtime=P+"KreyliRealtimeUi";
        String march=root+"MoveUnitsB/Line/MoveUnits_Line";
        String manager=root+"MenuManager", messageBox=root+"Messages/MessageBox_GameData", messages=P+"KreyliRealtimeMessages";
        if(name.equals(root+"Core/Core"))return realtimeArmyView(data);
        if(name.equals(root+"Civilization")||name.equals(root+"Province"))return trackStatistics(name,data);
        String country=root+"Menus/Civilization/Menu_InGame_Civ_Stats";
        boolean expertise=name.startsWith(root+"Menus/ZRest/Exp/Menu_InGame_MilitaryExp$");
        if(!expertise&&!Set.of(P+"MP",ui,browserBar,diplomacy,root+"CFG",root+"MenuManager",main,workshop,touch,touchManager,province,country,readyButton,buttonText,startup,newGame,gameAction,march,top+"1",top+"2",
                messageBox,root+"Save/SaveGameManager",root+"Menus/Turn/Menu_NextPlayerTurn",root+"Actions",root+"NewTurn",root+"AI/AI_Playstyle/AIPlaystyle").contains(name))return data;
        ClassReader reader=new ClassReader(data);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected ClassLoader getClassLoader(){return loader;}};
        reader.accept(new ClassVisitor(ASM8,writer){
            public FieldVisitor visitField(int a,String n,String d,String sig,Object value){
                if(name.equals(P+"MP")&&n.equals("MOD_VERSION"))value="1.0";
                if(name.equals(P+"MP")&&n.equals("VERSION")&&value instanceof String version)value=version+"|kreyli-coop-5";
                return super.visitField(a,n,d,sig,value);
            }
            public MethodVisitor visitMethod(int a,String n,String d,String sig,String[] ex){
                var mv=super.visitMethod(a,n,d,sig,ex);
                if(name.equals(touchManager)&&n.equals("mABX")&&d.equals("()V"))return new MethodVisitor(ASM8,mv){
                    int moves;
                    public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                        if(owner.equals(gameAction)&&method.equals("moveArmyAction")&&++moves==3){
                            super.visitVarInsn(ALOAD,5);
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeMarch","firstRegroupLeg",
                                    "(L"+gameAction+";IIIIZZL"+root+"RegroupArmy/RegroupArmy;)Z",false);
                        }else super.visitMethodInsn(opcode,owner,method,desc,itf);
                    }
                };
                if(name.equals(root+"CFG")&&(n.equals("drawVersion_LEFT_BOT")||n.equals("drawVersionLB"))){
                    mv.visitCode();mv.visitInsn(RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
                }
                if(name.equals(ui)&&n.equals("togglePlayers")){
                    return new MethodVisitor(ASM8,mv){
                        public void visitCode(){super.visitCode();
                            super.visitMethodInsn(INVOKESTATIC,P+"MP","lobbyScreen","()Z",false);
                            Label normal=new Label();super.visitJumpInsn(IFEQ,normal);
                            super.visitInsn(RETURN);super.visitLabel(normal);
                        }
                    };
                }
                if(name.equals(ui)&&n.equals("drawRoomCode")){
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
                        if(name.equals(diplomacy)&&d.startsWith("(II")&&d.endsWith(")V")
                                &&Set.of("sendAllianceProposal","acceptAllianceProposal","sendUnionProposal","sendUnionProposalFree",
                                    "acceptUnionProposal","sendNonAggressionProposal","acceptNonAggressionPact",
                                    "sendOfferVassalizationProposal","acceptOfferVassalization").contains(n)){
                            super.visitVarInsn(ILOAD,0);super.visitVarInsn(ILOAD,1);
                            super.visitMethodInsn(INVOKESTATIC,quick,"allowDiplomacy","(II)Z",false);
                            Label allowed=new Label();super.visitJumpInsn(IFNE,allowed);super.visitInsn(RETURN);super.visitLabel(allowed);
                        }
                        if(name.equals(diplomacy)&&n.equals("colonizeProvince")&&d.equals("(IIZ)Z")){
                            super.visitMethodInsn(INVOKESTATIC,quick,"allowColonization","()Z",false);
                            Label allowed=new Label();super.visitJumpInsn(IFNE,allowed);
                            super.visitInsn(ICONST_0);super.visitInsn(IRETURN);super.visitLabel(allowed);
                        }
                        if(name.equals(gameAction)&&n.equals("moveArmyAction")&&d.equals("(IIIIZZ)Z")){
                            super.visitVarInsn(ILOAD,2);super.visitVarInsn(ILOAD,4);
                            super.visitMethodInsn(INVOKESTATIC,quick,"allowMovement","(II)Z",false);
                            Label allowed=new Label();super.visitJumpInsn(IFNE,allowed);
                            super.visitInsn(ICONST_0);super.visitInsn(IRETURN);super.visitLabel(allowed);
                        }
                        if(name.equals(root+"AI/AI_Playstyle/AIPlaystyle")&&n.equals("moveAtWar")&&d.equals("(I)V")){
                            super.visitVarInsn(ILOAD,1);
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeAi","skipNativePlanning","(I)Z",false);
                            Label plan=new Label();
                            super.visitJumpInsn(IFEQ,plan);
                            super.visitInsn(RETURN);
                            super.visitLabel(plan);
                        }
                        if(name.equals(manager)&&n.equals("rebuildInGame_Messages")&&d.equals("()V")){
                            super.visitVarInsn(ALOAD,0);
                            super.visitMethodInsn(INVOKESTATIC,messages,"keepMenu","(L"+manager+";)Z",false);
                            Label rebuild=new Label();
                            super.visitJumpInsn(IFEQ,rebuild);
                            super.visitInsn(RETURN);
                            super.visitLabel(rebuild);
                        }
                        if(name.equals(root+"MenuManager")&&n.equals("setVisible_InGame_ProvinceAction")&&d.equals("(Z)V")){
                            super.visitVarInsn(ILOAD,1);
                            super.visitMethodInsn(INVOKESTATIC,realtime,"holdProvinceActions","(Z)Z",false);
                            Label nativeVisibility=new Label();
                            super.visitJumpInsn(IFEQ,nativeVisibility);
                            super.visitInsn(RETURN);
                            super.visitLabel(nativeVisibility);
                        }
                        if(name.equals(gameAction)&&n.equals("moveArmyAction")&&d.equals("(IIIIZZ)Z")){
                            for(int slot:new int[]{1,2,4})super.visitVarInsn(ILOAD,slot);
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeMarch","blocksMove","(III)Z",false);
                            Label allowed=new Label();
                            super.visitJumpInsn(IFEQ,allowed);
                            super.visitInsn(ICONST_0);
                            super.visitInsn(IRETURN);
                            super.visitLabel(allowed);
                        }
                        if(name.equals(gameAction)&&n.equals("mpEndInputOrders"))
                            super.visitMethodInsn(INVOKESTATIC,realtime,"beforeHostTurn","()V",false);
                        if(name.equals(touch)&&d.equals("(IIII)V")&&(n.equals("actionDown")||n.equals("actionUp"))){
                            for(int i=1;i<=4;i++)super.visitVarInsn(ILOAD,i);
                            super.visitMethodInsn(INVOKESTATIC,quick,n.equals("actionDown")?"touchDown":"touchUp","(IIII)Z",false);
                            Label quickDone=new Label();super.visitJumpInsn(IFEQ,quickDone);super.visitInsn(RETURN);super.visitLabel(quickDone);
                            for(int i=1;i<=4;i++)super.visitVarInsn(ILOAD,i);
                            super.visitMethodInsn(INVOKESTATIC,countryChoice,n.equals("actionDown")?"touchDown":"touchUp","(IIII)Z",false);
                            Label otherUi=new Label();super.visitJumpInsn(IFEQ,otherUi);super.visitInsn(RETURN);super.visitLabel(otherUi);
                            for(int i=1;i<=4;i++)super.visitVarInsn(ILOAD,i);
                            super.visitMethodInsn(INVOKESTATIC,profile,n.equals("actionDown")?"touchDown":"touchUp","(IIII)Z",false);
                            Label hudInput=new Label();super.visitJumpInsn(IFEQ,hudInput);super.visitInsn(RETURN);super.visitLabel(hudInput);
                            for(int i=1;i<=4;i++)super.visitVarInsn(ILOAD,i);
                            super.visitMethodInsn(INVOKESTATIC,hud,n.equals("actionDown")?"touchDown":"touchUp","(IIII)Z",false);
                            Label original=new Label();super.visitJumpInsn(IFEQ,original);super.visitInsn(RETURN);super.visitLabel(original);
                        }
                        if(name.equals(newGame)&&n.equals("draw")&&d.equals("(Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;IIZ)V")){
                            super.visitVarInsn(ALOAD,0);
                            super.visitMethodInsn(INVOKESTATIC,countryChoice,"limitGuestControls","(L"+root+"Menu;)V",false);
                        }
                        if(name.equals(newGame)&&n.equals("newGame")&&d.equals("()V")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliCountrySeats","beforeNewGame","()Z",false);
                            Label normal=new Label();super.visitJumpInsn(IFEQ,normal);super.visitInsn(RETURN);super.visitLabel(normal);
                        }
                        if(name.equals(buttonText)&&n.equals("drawTextE")){
                            for(int i=0;i<=4;i++)super.visitVarInsn(i<2?ALOAD:ILOAD,i);
                            super.visitMethodInsn(INVOKESTATIC,hud,"drawReadyText","(L"+root+"Button/MenuElemUI;Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;IIZ)Z",false);
                            Label normal=new Label();super.visitJumpInsn(IFEQ,normal);super.visitInsn(RETURN);super.visitLabel(normal);
                        }
                        if(name.equals(readyButton)&&n.equals("drawButtonBGE")){
                            super.visitVarInsn(ALOAD,0);super.visitVarInsn(ILOAD,2);super.visitVarInsn(ILOAD,3);
                            super.visitMethodInsn(INVOKESTATIC,hud,"anchorReady","(L"+root+"Button/MenuElemUI;II)V",false);
                        }
                        if(name.equals(browserBar)&&n.equals("actionEL")&&d.equals("(I)V")){
                            super.visitVarInsn(ILOAD,1);
                            Label hidden=new Label(),normal=new Label();
                            super.visitJumpInsn(IFEQ,hidden);
                            super.visitVarInsn(ILOAD,1);super.visitInsn(ICONST_5);super.visitJumpInsn(IF_ICMPNE,normal);
                            super.visitLabel(hidden);super.visitInsn(RETURN);super.visitLabel(normal);
                        }
                        if(name.equals(main)&&n.equals("actionEL")&&d.equals("(I)V")){super.visitVarInsn(ILOAD,1);super.visitMethodInsn(INVOKESTATIC,b,"openSinglePlayer","(I)Z",false);Label original=new Label();super.visitJumpInsn(IFEQ,original);super.visitInsn(RETURN);super.visitLabel(original);}
                        if(n.equals("clickEndTurn")&&name.endsWith("Menu_InGame_ProvInfo"))super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","beforeReadyClick","()V",false);
                    }
                    public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                        if(name.equals(root+"NewTurn")&&n.equals("doAction")&&owner.equals(messageBox)&&method.equals("updateNextTurn")){
                            super.visitMethodInsn(INVOKESTATIC,messages,"ageBox","(L"+messageBox+";I)V",false);
                            return;
                        }
                        if(name.equals(messageBox)&&n.equals("updateNextTurn")&&owner.equals(root+"Messages/Message")
                                &&method.equals("updateNextTurn")&&descriptor.equals("()Z")){
                            super.visitMethodInsn(INVOKESTATIC,messages,"ageMessage","(L"+root+"Messages/Message;)Z",false);
                            return;
                        }
                        if(name.equals(gameAction)&&owner.equals(root+"Core/Core")&&n.equals("resetTurnData")
                                &&method.equals("resetChooseProvinceData")){
                            super.visitMethodInsn(INVOKESTATIC,realtime,"resetTurnSelection","(L"+root+"Core/Core;)V",false);
                            return;
                        }
                        if(name.equals(gameAction)&&owner.equals(root+"MenuManager")){
                            if(n.equals("loadActivePlayerData")&&method.equals("setMenuIDWithoutAnim")){
                                super.visitMethodInsn(INVOKESTATIC,realtime,"showTurnView","(L"+root+"MenuManager;L"+root+"View;)V",false);
                                return;
                            }
                            if((n.equals("endOfInputOrders")||n.equals("turnMoves_UpdatePlayersFogOfWar"))
                                    &&method.equals("updateInGame_Top_All_NextTurnActions")){
                                super.visitMethodInsn(INVOKESTATIC,realtime,"updateTurnActions","(L"+root+"MenuManager;I)V",false);
                                return;
                            }
                        }
                        if(name.equals(root+"NewTurn")&&n.equals("updateBuildingsConstruction")&&owner.equals(root+"Civilization")&&method.equals("runConstructions")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeConstruction","resolveNative","(L"+root+"Civilization;)V",false);
                            return;
                        }
                        if(expertise&&owner.equals(root+"Core/Core")&&descriptor.equals("(I)Z")
                                &&(method.equals("addArmyExpertiseAttack")||method.equals("addArmyExpertiseDefense"))){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliMilitaryExpertise",
                                    method.equals("addArmyExpertiseAttack")?"attack":"defense","(L"+root+"Core/Core;I)Z",false);
                            return;
                        }
                        if(name.equals(root+"NewTurn")&&n.equals("doAction")&&owner.equals(root+"Save/SaveGameManager")&&method.equals("trySaveGame")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeAutosave","saveFromWorldUpdate","()V",false);
                            return;
                        }
                        if(name.equals(root+"NewTurn")&&n.equals("doAction")&&owner.equals(root+"Game/GameUpdate")&&method.equals("updateCivs_Money")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeEconomy","nativePayout","(L"+root+"Game/GameUpdate;)V",false);
                            return;
                        }
                        if(name.equals(root+"Actions")&&n.equals("doActions")&&owner.equals(root+"AI/AI")&&method.equals("turnOrders_End")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeMarch","finishAiOrders","(L"+root+"AI/AI;)V",false);
                            return;
                        }
                        if(name.equals(root+"Actions")&&n.equals("doActions")&&owner.equals(root+"MenuManager")&&method.equals("setVisibleInGame_Messages")){
                            super.visitMethodInsn(INVOKESTATIC,realtime,"showTurnMessages","(L"+root+"MenuManager;Z)V",false);
                            return;
                        }
                        if(name.equals(root+"Actions")&&owner.equals(root+"Civilization")&&method.equals("runRecruitArmyNT")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeRecruitment","resolveNative","(L"+root+"Civilization;)V",false);
                            return;
                        }
                        if(name.equals(root+"Menus/Turn/Menu_NextPlayerTurn")&&owner.equals(root+"MenuManager")&&method.equals("rebuildMenu_InGame_SavedGame")){
                            super.visitMethodInsn(INVOKESTATIC,realtime,"showAutomaticSave","(L"+root+"MenuManager;)V",false);
                            return;
                        }
                        if(name.equals(gameAction)&&n.equals("updatePlayerData")&&owner.equals(gameAction)&&method.equals("hideExtraViews")){
                            super.visitMethodInsn(INVOKESTATIC,realtime,"hideTurnViews","(L"+gameAction+";)V",false);
                            return;
                        }
                        if(name.equals(main)&&n.equals("draw")&&owner.equals(main)&&method.equals("beginClipM")){
                            super.visitVarInsn(ALOAD,1);
                            super.visitVarInsn(ILOAD,2);
                            super.visitVarInsn(ILOAD,3);
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliSplash","drawBackdrop","(Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;II)V",false);
                        }
                        if(name.equals(startup)&&n.equals("draw")&&owner.equals(root+"CFG")&&method.equals("drawJakowskiGamesRIGHT_BOT")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliSplash","draw",descriptor,false);
                        }else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                    }
                    public void visitFieldInsn(int opcode,String owner,String field,String descriptor){
                        if(name.equals(gameAction)&&n.equals("turnMoves_MoveCurrentArmy")&&owner.equals(gameAction)
                                &&opcode==GETFIELD&&field.equals("SHOW_REPORT")){
                            super.visitFieldInsn(opcode,owner,field,descriptor);
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeAlerts","showNativeReport","(Z)Z",false);
                            return;
                        }
                        if(name.equals(root+"MenuManager")&&n.equals("getViewID")&&opcode==GETFIELD
                                &&owner.equals(root+"GameValues/GV_InGame")&&field.equals("CIV_VIEW_STATS_SCALE_DOWN_IF_TOO_BIG")){
                            // Shrinking the viewport here clips the native stats and the profile row.
                            super.visitInsn(POP);
                            super.visitInsn(FCONST_1);
                            return;
                        }
                        if(name.equals(gameAction)&&opcode==PUTSTATIC
                                &&owner.equals(root+"Menus/Messages/Menu_InGame_Messages")&&field.equals("START_ANIMATION")
                                &&(n.equals("startNewTurn_End")||n.equals("loadActivePlayerData"))){
                            super.visitMethodInsn(INVOKESTATIC,realtime,"turnMessageAnimation","(Z)V",false);
                            return;
                        }
                        if(name.equals(browserBar)&&n.equals("onBackPressed")&&opcode==GETSTATIC
                                &&owner.equals(root+"View")&&field.equals("eMAINMENU"))field="eMP";

                        super.visitFieldInsn(opcode,owner,field,descriptor);
                        // Keep local mods loading, but never call native UGC without Steam.
                        if(name.equals(workshop)&&n.equals("loadSubscribedItems")&&opcode==GETSTATIC&&owner.equals(workshop)&&field.equals("sUQ")&&descriptor.equals("Z")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliSteam","available","()Z",false);
                            super.visitInsn(IAND);
                        }
                    }
                    public void visitLdcInsn(Object v){
                        if(name.equals(march)&&n.equals("updateMovingLine")&&Float.valueOf(600f).equals(v)){
                            super.visitLdcInsn(v);
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","marchMillis","(F)F",false);
                            return;
                        }
                        if(name.equals(root+"CFG")&&n.equals("gLGMP")&&" & LeoC".equals(v))v="";
                        if("2.2".equals(v))v="1.0";
                        if("https://discord.gg/GDTgYj2dJ".equals(v))v="https://discord.gg/zGdxgSvW4";
                        super.visitLdcInsn(v);
                    }
                    public void visitInsn(int opcode){
                        if(name.equals(manager)&&n.equals("rebuildInGame_Messages")&&d.equals("()V")&&opcode==RETURN){
                            super.visitVarInsn(ALOAD,0);
                            super.visitMethodInsn(INVOKESTATIC,messages,"rebuilt","(L"+manager+";)V",false);
                        }
                        if(name.equals(gameAction)&&n.equals("startNewTurn_End")&&opcode==RETURN)
                            super.visitMethodInsn(INVOKESTATIC,realtime,"afterHostTurn","()V",false);
                        if(name.equals(browserBar)&&n.equals("<init>")&&opcode==RETURN){
                            super.visitVarInsn(ALOAD,0);
                            super.visitMethodInsn(INVOKESTATIC,b,"browserBar","(L"+root+"Menu;)V",false);
                        }
                        if(name.equals(root+"Save/SaveGameManager")&&n.equals("saveGame_16")&&opcode==RETURN)super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","save","()V",false);
                        if(name.equals(root+"MenuManager")&&n.equals("drawMM")&&d.equals("(Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;)V")&&opcode==RETURN){
                            super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,b,"drawFooter",d,false);
                            super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,hud,"draw",d,false);
                            super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,profile,"draw",d,false);
                            super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,countryChoice,"draw",d,false);
                        }
                        if(topMenu&&n.equals("draw")&&d.equals("(Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;IIZ)V")&&opcode==RETURN){
                            super.visitVarInsn(ALOAD,0);super.visitVarInsn(ILOAD,2);super.visitVarInsn(ILOAD,3);
                            super.visitMethodInsn(INVOKESTATIC,hud,"anchorDate","(L"+root+"Menu;II)V",false);
                        }
                        if(name.equals(country)&&n.equals("<init>")&&opcode==RETURN){
                            super.visitVarInsn(ALOAD,0);
                            super.visitMethodInsn(INVOKESTATIC,profile,"reserveCountryPlayers","(L"+root+"Menu;)V",false);
                        }
                        if(name.equals(country)&&n.equals("draw")&&d.equals("(Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;IIZ)V")&&opcode==RETURN){
                            super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);super.visitVarInsn(ILOAD,2);super.visitVarInsn(ILOAD,3);
                            super.visitMethodInsn(INVOKESTATIC,profile,"drawCountry","(L"+root+"Menu;Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;II)V",false);
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
    private static byte[] realtimeArmyView(byte[] data){
        String descriptor="(Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;F)";
        var reader=new ClassReader(data);
        var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(ASM8,writer){
            public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                var original=super.visitMethod(access,name,desc,signature,exceptions);
                if(name.equals("autoChooseProvinceMode")&&desc.equals("(I)V"))return new MethodVisitor(ASM8,original){
                    public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                        if(owner.equals("age/of/civilizations2/jakowski/lukasz/Core/Core")
                                &&method.equals("updateHighlight_MoveUnitsProvinces")&&descriptor.equals("(I)V")){
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeUi","refreshMovementHighlights",
                                    "(L"+owner+";I)V",false);
                        }else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                    }
                };
                if(!name.equals("drawMoveUnitsArmy")||!desc.equals(descriptor+"V"))return original;
                return new MethodVisitor(ASM8,original){
                    public void visitCode(){
                        super.visitCode();
                        super.visitVarInsn(ALOAD,1);
                        super.visitVarInsn(FLOAD,2);
                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeMarch","drawArmies",descriptor+"Z",false);
                        var nativeView=new Label();
                        super.visitJumpInsn(IFEQ,nativeView);
                        super.visitInsn(RETURN);
                        super.visitLabel(nativeView);
                        super.visitFrame(F_SAME,0,null,0,null);
                    }
                };
            }
        },0);
        return writer.toByteArray();
    }

    private static byte[] trackStatistics(String type,byte[] data){
        boolean gold=type.endsWith("/Civilization");
        ClassReader reader=new ClassReader(data);
        // The prefix has no branches. Retain native frames to avoid resolving
        // Province recursively while its large class is being defined.
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(ASM8,writer){
            public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                MethodVisitor original=super.visitMethod(access,name,descriptor,signature,exceptions);
                if(gold&&name.equals("moveRegroupArmy")&&descriptor.equals("()V"))return new MethodVisitor(ASM8,original){
                    Label loop;
                    boolean patched;
                    public void visitLabel(Label label){
                        super.visitLabel(label);
                        if(!patched)loop=label;
                    }
                    public void visitJumpInsn(int opcode,Label label){
                        super.visitJumpInsn(opcode,label);
                        if(patched||opcode!=IF_ICMPGE)return;
                        patched=true;
                        super.visitVarInsn(ALOAD,0);
                        super.visitVarInsn(ILOAD,1);
                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeMarch","waitForRegroup","(L"+type+";I)Z",false);
                        Label proceed=new Label();
                        super.visitJumpInsn(IFEQ,proceed);
                        super.visitIincInsn(1,1);
                        super.visitJumpInsn(GOTO,loop);
                        super.visitLabel(proceed);
                        super.visitFrame(F_SAME,0,null,0,null);
                    }
                };
                if(!gold&&name.equals("loadProvinceBG"))return new MethodVisitor(ASM8,original){
                    public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                        if(opcode==INVOKESTATIC&&owner.equals("com/badlogic/gdx/graphics/PixmapIO")
                                &&method.equals("readCIM")&&desc.equals("(Lcom/badlogic/gdx/files/FileHandle;)Lcom/badlogic/gdx/graphics/Pixmap;")){
                            super.visitMethodInsn(opcode,P+"KreyliTextureCache","read",desc,false);
                        }else super.visitMethodInsn(opcode,owner,method,desc,itf);
                    }
                };
                if(gold?!(name.equals("setGold")&&descriptor.equals("(J)V"))
                       :!(name.equals("setCivId")&&descriptor.equals("(IZZ)V")))return original;
                return new MethodVisitor(ASM8,original){
                    public void visitCode(){
                        super.visitCode();
                        super.visitVarInsn(ALOAD,0);
                        super.visitVarInsn(gold?LLOAD:ILOAD,1);
                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliProfile",gold?"gold":"capture",
                                "(L"+type+";"+(gold?"J":"I")+")V",false);
                        if(!gold){
                            super.visitVarInsn(ALOAD,0);
                            super.visitVarInsn(ILOAD,1);
                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeAlerts","capture","(L"+type+";I)V",false);
                        }
                    }
                };
            }
        },0);
        System.out.println("SHARED_PATCH: statistics hook for "+type);
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
                                    if(name.equals(P+"MP_UI")&&method.equals("updateInGame")){
                                        super.visitMethodInsn(INVOKESTATIC,P+"MP","isOn","()Z",false);
                                        Label networked=new Label();super.visitJumpInsn(IFNE,networked);
                                        super.visitInsn(RETURN);super.visitLabel(networked);
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","hideRealtimeControls","()Z",false);
                                        Label keepNative=new Label();super.visitJumpInsn(IFEQ,keepNative);
                                        super.visitInsn(RETURN);super.visitLabel(keepNative);
                                    }
                                    if(name.equals(P+"MP_Net")&&method.equals("writeLine")){found.add(method);super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,P+"KreyliBrowser","advertise","(Ljava/lang/String;)Ljava/lang/String;",false);super.visitVarInsn(ASTORE,1);}
                                    if(name.equals(P+"MP_UI$PlayersTable")&&method.equals("actionEL")){
                                        found.add(method);super.visitVarInsn(ALOAD,0);super.visitVarInsn(ILOAD,1);
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliMenus","inviteAction","(Lage/of/civilizations2/jakowski/lukasz/Menu;I)Z",false);
                                        Label normal=new Label();super.visitJumpInsn(IFEQ,normal);super.visitInsn(RETURN);super.visitLabel(normal);
                                    }
                                    if(name.equals(P+"MP")&&method.equals("update")){
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliProfile","tick","()V",false);
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeAlerts","tick","()V",false);
                                    }
                                    if(name.equals(P+"MP")&&method.equals("stop")){
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliProfile","reset","()V",false);
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeAlerts","reset","()V",false);
                                    }
                                    if(name.equals(P+"MP"))switch(method){
                                        case "onEnterInGame"->{super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","prepare","()V",false);}
                                        case "onNewTurn"->super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeUi","resumeMarches","()V",false);
                                        case "refreshServers"->{super.visitInsn(ICONST_1);super.visitFieldInsn(PUTSTATIC,P+"MP","browseAll","Z");}
                                        case "join"->{super.visitMethodInsn(INVOKESTATIC,P+"KreyliIdentity","apply","()V",false);}
                                        case "onHello"->{super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","beforeHello","()V",false);super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,P+"KreyliIdentity","beforeHello","("+C+"[Ljava/lang/Object;)V",false);found.add(method);guard("rejectExtraPlayer","("+C+")Z",ALOAD,0);}
                                        case "loadSnapshot"->super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeUi","beforeSnapshot","()V",false);
                                        case "update"->{found.add(method);super.visitMethodInsn(INVOKESTATIC,P+"KreyliPings","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliInputRecovery","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSteam","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliLobby","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","tick","()V",false);}
                                        case "civFree"->{
                                            found.add(method);
                                            super.visitVarInsn(ILOAD,0);
                                            super.visitVarInsn(ALOAD,1);
                                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliCountrySeats","available","(I"+C+")Z",false);
                                            super.visitInsn(IRETURN);
                                        }
                                        case "pick"->{found.add(method);super.visitVarInsn(ILOAD,0);super.visitMethodInsn(INVOKESTATIC,P+"KreyliCountrySeats","pick","(I)Z",false);Label nativePick=new Label();super.visitJumpInsn(IFEQ,nativePick);super.visitInsn(RETURN);super.visitLabel(nativePick);}
                                        case "onClientLeft"->{found.add(method);guard("leave","("+C+")Z",ALOAD,0);}
                                        case "setPlayerLeader"->{found.add(method);guard("keepLeader","(I)Z",ILOAD,0);}
                                        case "onHostMessage"->{found.add(method);guard("message","("+C+"[Ljava/lang/Object;)Z",ALOAD,0,ALOAD,1);}
                                        case "updateBattleReports","showNextBattleReport"->{
                                            super.visitFieldInsn(GETSTATIC,P+"MP","reportsToShow","Ljava/util/List;");
                                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeAlerts","archiveQueued","(Ljava/util/List;)Z",false);
                                            Label showNative=new Label();super.visitJumpInsn(IFEQ,showNative);
                                            super.visitInsn(RETURN);super.visitLabel(showNative);
                                        }
                                        case "hasBattleReports"->{
                                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeAlerts","active","()Z",false);
                                            Label nativeReports=new Label();super.visitJumpInsn(IFEQ,nativeReports);
                                            super.visitInsn(ICONST_0);super.visitInsn(IRETURN);super.visitLabel(nativeReports);
                                        }
                                        case "onClientMessage"->{
                                            found.add(method);
                                            super.visitVarInsn(ALOAD,1);
                                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeAlerts","receive","([Ljava/lang/Object;)Z",false);
                                            Label otherMessage=new Label();super.visitJumpInsn(IFEQ,otherMessage);
                                            super.visitInsn(RETURN);super.visitLabel(otherMessage);super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,P+"KreyliLobby","clientMessage","([Ljava/lang/Object;)Z",false);Label normal=new Label();super.visitJumpInsn(IFEQ,normal);super.visitInsn(RETURN);super.visitLabel(normal);super.visitVarInsn(ALOAD,1);call("incoming","([Ljava/lang/Object;)V");}
                                        case "syncAfter"->{guard("lightSync","(Ljava/lang/String;)Z",ALOAD,0);}
                                        case "markReady"->{super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","markReady","()Z",false);Label normal=new Label();super.visitJumpInsn(IFEQ,normal);super.visitInsn(RETURN);super.visitLabel(normal);}
                                        case "endTurnPressed"->{
                                            found.add(method);
                                            super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","blockRealtimeReady","()Z",false);
                                            Label nativeReady=new Label();super.visitJumpInsn(IFEQ,nativeReady);
                                            super.visitInsn(RETURN);super.visitLabel(nativeReady);
                                            guard("cancelReady","()Z");guard("waitBeforeReady","()Z");
                                        }
                                        case "endTurn"->{found.add(method);call("canEnd","()Z");Label yes=new Label();super.visitJumpInsn(IFNE,yes);super.visitInsn(RETURN);super.visitLabel(yes);}
                                        case "sendSnap"->{found.add(method);super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);call("sending","("+C+"Ljava/util/concurrent/Future;)V");}
                                    }
                                }
                                public void visitLdcInsn(Object v){
                                    if(launcher&&"Age of History 2: Definitive Edition Multiplayer".equals(v)){v="Kreyli Coop AOH2";found.add("title");}
                                    if(v instanceof String s&&s.equals("AoH2DE-MP-2.2|2.01 Definitive Edition"))v=s+"|kreyli-coop-5";super.visitLdcInsn(v);
                                }
                                public void visitMethodInsn(int opcode,String owner,String called,String descriptor,boolean itf){
                                    String core="age/of/civilizations2/jakowski/lukasz/Core/Core";
                                    if(name.equals(P+"MP")&&method.equals("loadSnapshot")&&owner.equals(core)&&called.equals("setActiveProvID")){
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeUi","applySnapshotProvince","(L"+core+";I)V",false);
                                        return;
                                    }
                                    if(name.equals(P+"MP")&&called.equals("registerDirect")&&owner.equals(P+"MP_Net")
                                            &&descriptor.equals("(Ljava/lang/String;II)Ljava/net/Socket;")){
                                        super.visitFieldInsn(GETSTATIC,P+"MP","server","Ljava/net/ServerSocket;");
                                        super.visitMethodInsn(INVOKESTATIC,P+"SharedHostForm","registerDirect",
                                                "(Ljava/lang/String;IILjava/net/ServerSocket;)Ljava/net/Socket;",false);
                                        return;
                                    }
                                    String config="com/badlogic/gdx/backends/lwjgl3/Lwjgl3ApplicationConfiguration";
                                    if(launcher&&owner.equals(config)&&called.equals("setWindowIcon")){
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliWindow","icon","(L"+config+";Lcom/badlogic/gdx/Files$FileType;[Ljava/lang/String;)V",false);
                                        return;
                                    }
                                    if(launcher&&owner.equals(config)&&called.equals("setFullscreenMode")
                                            &&descriptor.equals("(Lcom/badlogic/gdx/Graphics$DisplayMode;)V")){
                                        super.visitMethodInsn(INVOKESTATIC,P+"KreyliWindow","borderless",
                                                "(L"+config+";Lcom/badlogic/gdx/Graphics$DisplayMode;)V",false);
                                        return;
                                    }
                                    super.visitMethodInsn(opcode,owner,called,descriptor,itf);
                                }
                                public void visitInsn(int opcode){
                                    if(name.equals(P+"MP_Save")&&method.equals("saveToDisk")&&opcode==RETURN)super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","save","()V",false);
                                    if(name.equals(P+"MP_UI")&&method.equals("updateInGame")&&opcode==RETURN)super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","updateUi","()V",false);
                                    if(name.equals(P+"MP_UI")&&method.equals("chatToggleMenu")&&opcode==ARETURN){found.add(method);}
                                    if(name.equals(P+"MP_Net")&&method.equals("listServers")&&opcode==ARETURN){found.add(method);super.visitMethodInsn(INVOKESTATIC,P+"KreyliBrowser","filter","(Ljava/util/List;)Ljava/util/List;",false);}
                                    if(name.equals(P+"MP_UI")&&method.equals("build")&&opcode==ARETURN){found.add(method);super.visitVarInsn(ALOAD,0);super.visitMethodInsn(INVOKESTATIC,P+"KreyliLobby","menus","([Lage/of/civilizations2/jakowski/lukasz/Menu;Lage/of/civilizations2/jakowski/lukasz/View;)[Lage/of/civilizations2/jakowski/lukasz/Menu;",false);}
                                    if(name.equals(P+"MP_UI$PlayersTable")&&method.equals("<init>")&&opcode==RETURN){found.add(method);super.visitVarInsn(ALOAD,0);super.visitMethodInsn(INVOKESTATIC,P+"KreyliMenus","appendInvite","(Lage/of/civilizations2/jakowski/lukasz/Menu;)V",false);}
                                    if(name.equals(P+"MP")){
                                        if(opcode==RETURN){if(method.equals("update")){super.visitMethodInsn(INVOKESTATIC,P+"KreyliOrderSync","tick","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","tick","()V",false);}if(method.equals("onNewTurn"))super.visitMethodInsn(INVOKESTATIC,P+"KreyliTurns","newTurn","()V",false);if(method.equals("onEnterInGame"))super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","restoreConnected","()V",false);}
                                        if(method.equals("loadSettings")&&opcode==RETURN){found.add(method);super.visitMethodInsn(INVOKESTATIC,P+"KreyliIdentity","apply","()V",false);}
                                        if(method.equals("t")&&opcode==ARETURN){
                                            found.add(method);
                                            super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);super.visitVarInsn(ALOAD,2);
                                            call("brandedText","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;");
                                        }
                                        if(method.equals("startHost")&&opcode==IRETURN){found.add(method);super.visitInsn(DUP);Label skip=new Label();super.visitJumpInsn(IFEQ,skip);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSteam","hostStarted","()V",false);super.visitLabel(skip);}
                                        if(method.equals("onCmd")&&opcode==RETURN){found.add(method);call("afterCommand","()V");}
                                        if(method.equals("assign")&&opcode==RETURN){super.visitVarInsn(ALOAD,0);super.visitVarInsn(ILOAD,1);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","assigned","("+C+"I)V",false);}
                                        if(method.equals("stop")&&opcode==RETURN){found.add(method);call("reset","()V");super.visitMethodInsn(INVOKESTATIC,P+"KreyliSaveSeats","reset","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliSteam","leaveSession","()V",false);super.visitMethodInsn(INVOKESTATIC,P+"KreyliLobby","reset","()V",false);}
                                        if(method.equals("loadSnapshot")&&opcode==RETURN){found.add(method);super.visitVarInsn(ALOAD,1);call("loaded","(Ljava/lang/Object;)V");super.visitMethodInsn(INVOKESTATIC,P+"KreyliRealtimeUi","afterSnapshot","()V",false);}
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
        ClassLoader gameLoader=ClassLoader.getSystemClassLoader();
        Class.forName("age.of.civilizations2.jakowski.lukasz.Civilization",false,gameLoader);
        Class.forName("age.of.civilizations2.jakowski.lukasz.Province",false,gameLoader);
    }
}











