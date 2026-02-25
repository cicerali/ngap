import generated.ngap.*;
import org.junit.jupiter.api.Test;
import tr.com.cicerali.aper.BitStreamReader;
import tr.com.cicerali.aper.BitStreamWriter;
import tr.com.cicerali.aper.BitString;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NGAPTest {

    @Test
    void testAperEncodeDecode() throws Exception {
        byte[] expected = Files.readAllBytes(Paths.get("src/test/resources/NGSetupRequest.bin"));
        NGAP_PDU decodedPDU = NGAP_PDU.decodeAPER(new BitStreamReader(expected));
        assertNotNull(decodedPDU);
        assertEquals(21, decodedPDU.getInitiatingMessage().getProcedureCode().getValue());
        assertInstanceOf(NGSetupRequest.class, decodedPDU.getInitiatingMessage().getValue());
        NGSetupRequest ngSetupRequest = (NGSetupRequest) decodedPDU.getInitiatingMessage().getValue();
        assertInstanceOf(ProtocolIE_Container.class, ngSetupRequest.getProtocolIEs());
        ProtocolIE_Container protocolIEs = ngSetupRequest.getProtocolIEs();
        assertEquals(4, protocolIEs.getItems().size());
        ProtocolIE_Field protocolIEField1 = protocolIEs.getItems().get(0);
        assertEquals(27, protocolIEField1.getId().getValue());
        assertEquals("reject", protocolIEField1.getCriticality().getValue().name());
        GlobalRANNodeID globalRANNodeID = (GlobalRANNodeID) protocolIEField1.getValue();
        assertNotNull(globalRANNodeID);
        GlobalGNB_ID globalGNBID = globalRANNodeID.getGlobalGNB_ID();
        assertEquals("02F839", globalGNBID.getPLMNIdentity().getValue());
        BitSet bits = BitSet.valueOf(new long[]{Long.parseLong("000000000000000100000010", 2)});
        BitString bitString = new BitString(bits, 24);
        assertEquals(bitString, globalGNBID.getGNB_ID().getGNB_ID());

        ProtocolIE_Field protocolIEField2 = ngSetupRequest.getProtocolIEs().getItems().get(1);
        assertEquals(82, protocolIEField2.getId().getValue());
        assertEquals("ignore", protocolIEField2.getCriticality().getValue().getAsn1Name());
        assertNotNull(protocolIEField2.getValue());
        RANNodeName ranNodeName = (RANNodeName) protocolIEField2.getValue();
        assertEquals("free5gc", ranNodeName.getValue());

        BitStreamWriter bitStreamWriter = new BitStreamWriter();
        decodedPDU.encodeAPER(bitStreamWriter);
        assertArrayEquals(expected, bitStreamWriter.toByteArray());
    }

    @Test
    void testAperEncodeDecode2() throws Exception {
        NGAP_PDU ngAPPDU = new NGAP_PDU();
        InitiatingMessage initiatingMessage = new InitiatingMessage();
        ProcedureCode procedureCode = new ProcedureCode();
        procedureCode.setValue(21L);
        initiatingMessage.setProcedureCode(procedureCode);
        Criticality criticality = new Criticality();
        criticality.setValue(Criticality.Value.reject);
        initiatingMessage.setCriticality(criticality);

        NGSetupRequest ngSetupRequest = new NGSetupRequest();
        ProtocolIE_Container protocolIEContainer = new ProtocolIE_Container();

        List<ProtocolIE_Field> items = new ArrayList<>();

        ProtocolIE_Field protocolIEField = new ProtocolIE_Field();
        ProtocolIE_ID protocolIEID = new ProtocolIE_ID();
        protocolIEID.setValue(27L);
        protocolIEField.setId(protocolIEID);
        criticality = new Criticality();
        criticality.setValue(Criticality.Value.reject);
        protocolIEField.setCriticality(criticality);


        GlobalRANNodeID globalRANNodeID = new GlobalRANNodeID();
        GlobalGNB_ID globalGNBID = new GlobalGNB_ID();
        PLMNIdentity plmnIdentity = new PLMNIdentity();
        plmnIdentity.setValue("02F839");
        GNB_ID gnbId = new GNB_ID();
        BitSet bits = BitSet.valueOf(new long[]{Long.parseLong("000000000000000100000010", 2)});
        BitString bitString = new BitString(bits, 24);
        gnbId.setGNB_ID(bitString);
        globalGNBID.setGNB_ID(gnbId);
        globalGNBID.setPLMNIdentity(plmnIdentity);
        globalRANNodeID.setGlobalGNB_ID(globalGNBID);
        protocolIEField.setValue(globalRANNodeID);
        items.add(protocolIEField);

        protocolIEField = new ProtocolIE_Field();
        protocolIEID = new ProtocolIE_ID();
        protocolIEID.setValue(82L);
        protocolIEField.setId(protocolIEID);
        criticality = new Criticality();
        criticality.setValue(Criticality.Value.ignore);
        protocolIEField.setCriticality(criticality);

        RANNodeName ranNodeName = new RANNodeName();
        ranNodeName.setValue("free5gc");
        protocolIEField.setValue(ranNodeName);
        items.add(protocolIEField);

        protocolIEField = new ProtocolIE_Field();
        protocolIEID = new ProtocolIE_ID();
        protocolIEID.setValue(102L);
        protocolIEField.setId(protocolIEID);
        criticality = new Criticality();
        criticality.setValue(Criticality.Value.reject);
        protocolIEField.setCriticality(criticality);

        SupportedTAList supportedTAList = new SupportedTAList();
        SupportedTAItem supportedTAItem = new SupportedTAItem();
        TAC tAC = new TAC();
        tAC.setValue("000001");
        supportedTAItem.setTAC(tAC);
        BroadcastPLMNList broadcastPLMNList = new BroadcastPLMNList();
        BroadcastPLMNItem broadcastPLMNItem = new BroadcastPLMNItem();
        PLMNIdentity pLMNIdentity = new PLMNIdentity();
        pLMNIdentity.setValue("02F839");
        broadcastPLMNItem.setPLMNIdentity(pLMNIdentity);
        SliceSupportList tAISliceSupportList = new SliceSupportList();
        SliceSupportItem sliceSupportItem = new SliceSupportItem();
        S_NSSAI sNSSAI = new S_NSSAI();
        SST sst = new SST();
        sst.setValue("01");
        sNSSAI.setSST(sst);
        SD sD = new SD();
        sD.setValue("010203");
        sNSSAI.setSD(sD);
        sliceSupportItem.setS_NSSAI(sNSSAI);
        tAISliceSupportList.getItems().add(sliceSupportItem);

        broadcastPLMNItem.setTAISliceSupportList(tAISliceSupportList);
        broadcastPLMNList.getItems().add(broadcastPLMNItem);
        supportedTAItem.setBroadcastPLMNList(broadcastPLMNList);
        supportedTAList.getItems().add(supportedTAItem);
        protocolIEField.setValue(supportedTAList);
        items.add(protocolIEField);


        protocolIEField = new ProtocolIE_Field();
        protocolIEID = new ProtocolIE_ID();
        protocolIEID.setValue(21L);
        protocolIEField.setId(protocolIEID);
        criticality = new Criticality();
        criticality.setValue(Criticality.Value.ignore);
        protocolIEField.setCriticality(criticality);

        PagingDRX pagingDRX = new PagingDRX();
        pagingDRX.setValue(PagingDRX.Value.v128);

        protocolIEField.setValue(pagingDRX);
        items.add(protocolIEField);

        protocolIEContainer.setItems(items);
        ngSetupRequest.setProtocolIEs(protocolIEContainer);
        initiatingMessage.setValue(ngSetupRequest);
        ngAPPDU.setInitiatingMessage(initiatingMessage);

        // XER
        StringWriter xerWriter = new StringWriter();
        XMLStreamWriter xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(xerWriter);
        ngAPPDU.encodeXER(xmlWriter);
        xmlWriter.flush();
        String xerOutput = xerWriter.toString();
        assertNotNull(xerOutput);
        System.out.println("\nEncoded XER (NGAP-PDU):\n" + XMLHelper.prettyPrintXML(xerOutput));

        String expectedXer = new String(Files.readAllBytes(Paths.get("src/test/resources/NGSetupRequest.xer")));
        assertEquals(expectedXer.replaceAll("\\s+", ""), xerOutput.replaceAll("\\s+", ""));

        BitStreamWriter bitStreamWriter = new BitStreamWriter();
        ngAPPDU.encodeAPER(bitStreamWriter);
        byte[] expected = Files.readAllBytes(Paths.get("src/test/resources/NGSetupRequest.bin"));
        byte[] bytes = bitStreamWriter.toByteArray();
        assertArrayEquals(expected, bytes);
    }
}
