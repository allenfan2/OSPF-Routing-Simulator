/**
 * Created by Allen on 11/28/2018.
 */
import javafx.util.Pair;

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class router {


    //HELPER FUNCTION FOR CONVERTING BYTE ARRAY TO INT ARRAY
    private static int [] BtoI(byte [] array){
        IntBuffer int_buffer = ByteBuffer.wrap(array).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int [] int_array = new int [int_buffer.remaining()];
        int_buffer.get(int_array);
        return int_array;
    }
    //HELPER FUNCTION FOR CONVERTING INT ARRAY TO BYTE ARRAY
    private static byte [] ItoB(int[] array){
        ByteBuffer bBuffer = ByteBuffer.allocate(4*array.length);
        bBuffer.order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer intBuffer = bBuffer.asIntBuffer();
        intBuffer.put(array);
        return bBuffer.array();
    }
    //MAKING BYTE ARRAY OF LSPDU Packets
    private static byte [] makeLSPDU(int sender, int router_id, int link_id, int cost, int via){
        int [] LSPDUIntArray = new int[]{sender,router_id,link_id,cost,via};
        return ItoB(LSPDUIntArray);
    }
    //a list of link costs, used to represent a number of entries for a specific router_id in the LSDB/
    public static class lcList{
        List<link_cost> list;
        public lcList(){
            list = new ArrayList<link_cost>();
        }
        public void add(link_cost lc){
            list.add(lc);
        }
        public int size(){
            return list.size();
        }
        public link_cost get(int i){
            return list.get(i);
        }
        public boolean exist(int link_id){
            for(int i=0;i<list.size();++i){
                link_cost temp = list.get(i);
                if(temp.link_id == link_id){
                    return true;
                }
            }
            return false;
        }
    }
    // Dijkstra sorting algorithm, given a agency matrix and src, return a distance array and predecessor array in a pair.
    public static Pair<int[],int[]> DKSTRA (int [][] graph, int src){
        int n = 5;
        ArrayList<Integer> N = new ArrayList<>();  // Initialization of arrays
        int dist[] = new int[5];
        int pred[] = new int[]{-1,-1,-1,-1,-1};
        N.add(src); //add the src to N array

        for(int i=0; i < n; ++i){ //Initializing values for neighbours and non neighbors.
            if(graph[src][i] != 0){
                dist[i]=graph[src][i];
                pred[i]=src;
            } else {
                dist[i]=Integer.MAX_VALUE;
            }
        }
        pred[src] = -1; //SRC has no predecessor
        dist[src] = 0; //SRC's distance to itself is 0
        while(N.size()<5){
            int min = Integer.MAX_VALUE;
            int index = -1;
            for(int i =0; i < n; ++i){ //Finding min value in distance.
                if (N.contains(i)){
                    continue;
                }
                if(dist[i] < min){
                    min = dist[i];
                    index = i;
                }
            }
            if(index == -1){ //If there is no min value leave break.
                break;
            }
            N.add(index);
            for(int i =0; i<n; ++i){
                if((graph[index][i]!= 0) && !N.contains(i)){
                    //Take the minimum of the current distance or the other path through index
                    dist[i] = Math.min(dist[i],dist[index]+graph[index][i]);
                    if(dist[i] < (dist[index]+graph[index][i])){
                        pred[i] = pred[i]; //Keep the same predecessor.
                    } else {
                        //Change the predecessor's to index (if index isn't a neighbour of src keep traversing until it is.)
                        int x = index;
                        while(pred[x]!=src){
                            x = pred[x];
                        }
                        pred[i] = x;
                    }
                }
            }
        }

        Pair<int[],int[]> array = new Pair<>(dist,pred);
        return array;
    }

    private static class link_cost{ //Link Cost Structure
        private int link_id;
        private int cost;
        private link_cost(int link_id ,int cost){
            this.link_id = link_id;
            this.cost = cost;
        }
    }


    public static void main(String[] args) throws Exception {

        //READING COMMAND LINE ARGUMENTS
        int r_id = Integer.parseInt(args[0]);
        String nse_host = args[1];
        int nse_port = Integer.parseInt(args[2]);
        int r_port = Integer.parseInt(args[3]);

        //FILE WRITER INITIALIZATION
        String fname = "router"+r_id+".log";
        PrintWriter logfile = new PrintWriter(fname);
        logfile.println("# Log Begins");

        //LSDB Initialization
        lcList[] LSDB = new lcList[5];
        for(int i = 0; i < LSDB.length; ++i){
            LSDB[i]= new lcList();
        }

        //Creating InitPacket
        int [] initArray = new int []{r_id};
        byte [] initByteArray = ItoB(initArray);
        //SOCKET Initialization
        DatagramSocket nseSocket = new DatagramSocket(r_port);
        InetAddress nseIP = InetAddress.getByName(nse_host);
        DatagramPacket init_pkt = new DatagramPacket(initByteArray,initByteArray.length,nseIP,nse_port);
        //SENDING THE INIT PACKET
        nseSocket.send(init_pkt);
        logfile.println("R"+r_id+" sends an INIT packet: router_id "+r_id);
        //


        //Receiving Circuit DB
        byte [] cDBArray = new byte [44];
        DatagramPacket t_pkt = new DatagramPacket(cDBArray,44);
        nseSocket.receive(t_pkt);
        int [] cDIArray = BtoI(cDBArray);
        int nbr = cDIArray [0];
        logfile.println("R"+r_id+" receives CIRCUIT DB: nbr link "+nbr);
        //SAVING CIRCUIT DATABASE
        for(int i=1 ; i < nbr*2 ; i+=2){
            LSDB[r_id-1].add(new link_cost(cDIArray[i],cDIArray[i+1]));
        }

        //SENDING OUT HELLO PACKETS to LINKs received from circuit DB
        for(int i=0; i < nbr; ++i){
            int link_id = LSDB[r_id-1].get(i).link_id;
            int [] helloPKTIntArray = new int []{r_id,link_id};
            byte [] helloPKTByteArray = ItoB(helloPKTIntArray);
            DatagramPacket helloPKT = new DatagramPacket(helloPKTByteArray,helloPKTByteArray.length,nseIP,nse_port);
            nseSocket.send(helloPKT);
            logfile.println("R"+r_id+" sends a HELLO: router_id "+r_id + " link_id "+link_id);
        }

        //Arrays to keep track of neighbours and their links
        List<Integer> neighbours = new ArrayList<>();
        List<Integer> nLink = new ArrayList<>();

        //}

        int [][] adjacency_matrix = new int [5][5]; // Adjacency list initialization.

        while(true) {
            byte[] PktByteArray = new byte[20];
            nseSocket.setSoTimeout(100);
            DatagramPacket incomePkt = new DatagramPacket(PktByteArray, 20);
            try {  //RECEIVING INCOMING PACKET
                nseSocket.receive(incomePkt);
            } catch (SocketTimeoutException timeout) {
                break;
            }
            int [] LSPDUIntArray = BtoI(PktByteArray);

            int sender = LSPDUIntArray[0];
            int router_id = LSPDUIntArray[1];
            int link_id = LSPDUIntArray[2];
            int cost = LSPDUIntArray[3];
            int from = LSPDUIntArray[4];

            if(LSPDUIntArray[3] == 0){ // RECEIVED HELLO PACKET
                neighbours.add(LSPDUIntArray[0]);
                nLink.add(LSPDUIntArray[1]);
                logfile.println("R"+r_id+" receives a HELLO: router_id "+sender + " link_id "+router_id);
                for(int i = 0; i < LSDB[r_id-1].size();++i){
                    int link = LSDB[r_id-1].get(i).link_id;
                    int cst = LSDB[r_id-1].get(i).cost;
                    byte[]initLSPDU = makeLSPDU(r_id,r_id,link,cst,LSPDUIntArray[1]);
                    DatagramPacket resendLSPDU = new DatagramPacket(initLSPDU, initLSPDU.length, nseIP, nse_port);
                    nseSocket.send(resendLSPDU);
                    //SENDING LSPDU PACKET to Hello Packet sender.
                    logfile.println("R"+r_id+" sends an LS PDU: sender "+r_id+", router_id "+r_id+", link_id "+link+", cost "+cst+", via "+LSPDUIntArray[1]);
                }
                continue;
            }

            if(LSDB[router_id-1].exist(link_id)){ // IF A LSDB Entry already exists, ignore it and skip.
                continue;
            }

            logfile.println("R"+r_id+" receives an LS PDU: sender "+sender+", router_id "+router_id+", link_id "+link_id+", cost "+cost+", via "+from);

            LSDB[router_id-1].add(new link_cost(link_id,cost)); // Update LSDB
            int matched_rid = -1; //SEE if the added information matches with a link already in the LSDB.
            for(int i = 0; i<LSDB.length; ++i){
                if(i==router_id-1){
                    continue;
                }
                for(int j=0; j<LSDB[i].size();++j){
                    if (LSDB[i].get(j).link_id == link_id){
                        matched_rid = i;
                    }
                }
            }

            if(matched_rid != -1){ //If Link Matches, Update the Adjacency Matrix
                adjacency_matrix[matched_rid][router_id-1]=cost;
                adjacency_matrix[router_id-1][matched_rid]=cost;
            }
            logfile.println("# Topology database"); //PRINTING TOPOLOGY DATABASE
            for(int i=0; i<LSDB.length;++i){
                int s = LSDB[i].size();
                if(s == 0){
                    continue;
                }
                logfile.println("R"+r_id+" -> R"+(i+1)+" nbr link "+s);
                for(int j=0;j<LSDB[i].size();++j){
                    link_cost lc = LSDB[i].get(j);
                    logfile.println("R"+r_id+" -> R"+(i+1)+" link "+lc.link_id + " cost "+lc.cost);
                }
            }

            //Running Dijkstra's algorithm on the graph model. Getting distance and pred array  back.
            Pair<int[],int[]> array = DKSTRA(adjacency_matrix,r_id-1);

            logfile.println("# RIB");
            //PRINTING OUT THE RIB
            for(int i=0; i<5; ++i){
                int pred = array.getValue()[i];
                int cst = array.getKey()[i];
                // An Edge case for if i is a neighbour than we set its pred to itself.
                if(neighbours.contains(i+1)&&pred==r_id-1){
                    pred = i;
                }
                if(pred == -1){ //If the predecessor is -1, its either the router itself or something unreachable.
                    if (i==r_id-1) { //router itself
                        logfile.println("R" + r_id + " -> R" + (i + 1) + " -> Local, 0");
                    }else{ //not yet reachable router
                        logfile.println("R" + r_id + " -> R" + (i + 1) + " -> INF, INF");
                    }
                }else{ // all other cases
                    logfile.println("R"+r_id+" -> R"+(i+1)+" -> R"+(pred+1)+", " +cst);
                }
            }
            //SENDING NEWLY RECEIVED UNIQUE NON DUPLICATE LSPDUs to neighbors.
            for(int i = 0; i< neighbours.size(); ++i){
                int n_id = neighbours.get(i);
                if (sender!=n_id){ //if the neighbour isn't the sender of the LSPDU.
                    int [] resend = new int[]{r_id,router_id,link_id,cost,nLink.get(i)};
                    byte [] resendByteArray = ItoB(resend);
                    DatagramPacket resendLSPDU = new DatagramPacket(resendByteArray, resendByteArray.length, nseIP, nse_port);
                    nseSocket.send(resendLSPDU);
                    logfile.println("R"+r_id+" sends an LS PDU: sender "+r_id+", router_id "+router_id+", link_id "+link_id+", cost "+cost+", via "+nLink.get(i));
                }
            }
        }
        logfile.println("# Log Ends");
        logfile.close();


    }
}
