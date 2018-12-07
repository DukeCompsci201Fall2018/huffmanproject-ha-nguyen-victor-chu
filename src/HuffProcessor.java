import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
		
	}
	
	public void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out)
	//takes the message and encodes it
	{
		
		while(true) {
			int bit = in.readBits(8);
			if(bit == -1) break;
			String code= codings[bit];//this corresponds to the encodings
			
			int v = Integer.parseInt(code,2);
			out.writeBits(code.length(),v);
			//converts to a binary string
		}
	
		
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(),Integer.parseInt(code,2));
	}
	
	public void writeHeader(HuffNode root, BitOutputStream out)
	{
		if(root.myLeft == null && root.myRight == null)
		{
			//need to write it twice
			//way we write the leaf, is the 1, follow by the value. 
			out.writeBits(1,1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
			return;
		}
		else
			out.writeBits(1,0);//value is 0 for internal nodes
			writeHeader(root.myLeft,out);
			writeHeader(root.myRight,out);
		
	}
	
	public String[] makeCodingsFromTree(HuffNode root)
	{
		String[] encodings = new String[ALPH_SIZE+1];
		codingHelper(root, "",encodings);
		return encodings;
		
	}
	
	public void codingHelper(HuffNode root, String path, String[] encodings)
	{

    	if(root == null)return;
    	
    	//the path we take is in 0s and 1s to the 8 bit chunk
    	if(root.myRight == null && root.myLeft == null)
    	{
    		encodings[root.myValue] = path;//how we get ot eh character
    		return;
    	}
    	
    	codingHelper(root.myLeft, path + "0",encodings);
    	codingHelper(root.myRight, path + "1",encodings);
	}
	
	public HuffNode makeTreeFromCounts(int[] counts)
	{
		PriorityQueue<HuffNode> pq = new PriorityQueue<HuffNode>();
		for(int i = 0; i < counts.length;i++)
		{
			if(counts[i] > 0)
			{
				pq.add(new HuffNode(i, counts[i],null,null));
			}
		}//build the priority queue, makes sense
			//we remove the least occuring character first
		
		while(pq.size() > 1)
		{
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight+ right.myWeight, left, right);//intermediate node
			//what to put as value?
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	
	public int[] readForCounts(BitInputStream in)
	{
		int[] freq = new int[ALPH_SIZE+1];
		for(int i = 0; i < freq.length;i++)
		{
			int bitvalue = in.readBits(BITS_PER_WORD);
			if(bitvalue == -1) 
			{
				freq[PSEUDO_EOF] =1; 
				break;
			}
			freq[bitvalue]++;//increase the occurance
		}
		
		return freq;
	}
	
	/**s
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if (bits!= HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
		
	}


	public void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		//read a single bit
		
		HuffNode current = root; 
		while(true)
		{
			int bits = in.readBits(1);
			if(bits == -1)
			{
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if(bits==0) 
					current = current.myLeft;
				else
					current = current.myRight;
				
				if(current.myLeft == null && current.myRight == null)
				{
					if(current.myValue == PSEUDO_EOF)
						break;
					
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;//need to retraverse the tree
					}
				}
			}
		}
		
	}

	
	
	public HuffNode readTreeHeader(BitInputStream in) {
		//this reads the header.
		//after reading the first 
		//each letter is the 9 bit in our tree.
		
		int bit = in.readBits(1);//reads the next bit
		if (bit == -1) throw new HuffException("reading bits failed");
		if (bit == 0) {//explore left and right subtrees until we get to a letter
		    HuffNode left =  readTreeHeader(in);
		    HuffNode right =  readTreeHeader(in);//recursive calls
		    return new HuffNode(0,0,left,right);//internal nodes
		}
		else {
		    int value = in.readBits(BITS_PER_WORD + 1);
		    //do BITS_PER_WORD+1 for the 9 bits
		    return new HuffNode(value,0,null,null);//leafs
		}
		
	
	}
	
}