package project1;

import java.io.IOException;
import java.util.StringTokenizer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;


public class Query4 extends Configured implements Tool {
	
	
	
	public static class JoinMapper
			extends Mapper<Object, Text, Text, Text>{
		//Save records for Customers relation
		private HashMap<String, String> customerMap = new HashMap<String, String>();
		private Text cCode = new Text();
		private Text mapValue = new Text();
	
		//Read Customers.csv file into DistributedCache for map-side join
		@Override
		protected void setup(Context context
				) throws IOException, InterruptedException{
			BufferedReader bf = null;
			Path[] paths = DistributedCache.getLocalCacheFiles(context.getConfiguration());
			String line = "";
			
			for(Path path: paths){
				if(path.toString().contains("Customers")){
					bf = new BufferedReader(new FileReader(path.toString()));
					while((line=bf.readLine())!=null){
						// save to HashMap
						String[] str= line.split(",",2);
						customerMap.put(str[0], str[1]);
					}
				}
			}
			
			bf.close();
		} // finish setup
		
		public void map(Object key, Text value, Context context
				)throws IOException, InterruptedException{
			StringTokenizer itr = new StringTokenizer(value.toString());
			while(itr.hasMoreTokens()){
				String[] recordT = itr.nextToken().toString().split(",");
				String[] recordC = customerMap.get(recordT[1]).split(",");
				// CountryCode as key, since it has already matched the customer id;
				cCode.set(recordC[2]);
				//  #id transNum
				mapValue.set("1 "+recordT[1]+" "+recordT[2]);
				context.write(cCode, mapValue);
			}
		}
	
	}// finish mapper
	
	public static class sumReducer 
			extends Reducer<Text, Text, Text, Text>{
		private Text result = new Text();
		private HashMap<String, Float> cus = new HashMap<String, Float>();
		public void reduce(Text key, Iterable<Text> value, Context context
				) throws IOException, InterruptedException{
			int sum = 0;	//#transacion
			float max = Float.MIN_VALUE;		//total money of transactions
			float min = Float.MAX_VALUE;	//min transItem
			for(Text val:value){
				String[] rec = val.toString().split(" ");
				sum += Integer.parseInt(rec[0]);
				if(!cus.containsKey(rec[1])){
					cus.put(rec[1], Float.parseFloat(rec[2]));
				}else{
					cus.put(rec[1], cus.get(rec[1])+Float.parseFloat(rec[2]));
				}
			}
			for(String a: cus.keySet()){
				max = cus.get(a)>max? cus.get(a):max;
				min = cus.get(a)<min? cus.get(a):min;
			}
			result.set(Integer.toString(sum)+" "+Float.toString(min)+" "+Float.toString(max));
			context.write(key, result);
		}
	}// finish reducer

	@Override
	public int run(String[] arg0) throws Exception {
		Configuration conf = new Configuration();
		Job job = new Job(conf, "Query4");
		job.setJarByClass(Query4.class);
		job.setMapperClass(JoinMapper.class);
		job.setReducerClass(sumReducer.class);
		job.setNumReduceTasks(2);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		DistributedCache.addCacheFile(new Path(arg0[0]).toUri(), job
                .getConfiguration());
		FileInputFormat.addInputPath(job, new Path(arg0[1]));
		FileOutputFormat.setOutputPath(job, new Path(arg0[2]));
		
		return job.waitForCompletion(true) ? 0 : 1;
	}
	
	public static void main(String[] args) throws Exception{
		int res = ToolRunner.run(new Configuration(), new Query4(),
                args);
        System.exit(res);
	}
}
