// Native, neutral-by-default channel processing. Parameters change gradually.
struct LevelMeter {
    std::atomic<float> left{0},right{0};
    void add(float l,float r){auto put=[](std::atomic<float>& value,float peak){float previous=value.load(std::memory_order_relaxed);while(peak>previous&&!value.compare_exchange_weak(previous,peak,std::memory_order_relaxed)){};};put(left,std::abs(l));put(right,std::abs(r));}
    var read(){float l=left.exchange(0),r=right.exchange(0);return Array<var>{double(l),double(r)};}
};
struct ChannelDsp {
    bool capture=false;int spectrumAt=0;std::array<float,16384> spectrumBefore{},spectrumAfter{};
    void tap(float before,float after){if(!capture)return;spectrumBefore[spectrumAt]=before;spectrumAfter[spectrumAt]=after;spectrumAt=(spectrumAt+1)%16384;}
    struct Filter {
        double c[5]{1,0,0,0,0},target[5]{1,0,0,0,0},z1[2]{},z2[2]{};
        float process(float x,int ch){double y=c[0]*x+z1[ch];z1[ch]=c[1]*x-c[3]*y+z2[ch];z2[ch]=c[2]*x-c[4]*y;return std::isfinite(y)?float(y):0;}
        void smooth(){for(int i=0;i<5;i++)c[i]+=(target[i]-c[i])*.002;}
        void setup(int type,double freq,double gain,double q,double rate){
            freq=std::clamp(freq,20.0,rate*.45);q=std::clamp(q,.1,10.0);const double a=std::pow(10.0,gain/40),w=2*MathConstants<double>::pi*freq/rate,cs=std::cos(w),sn=std::sin(w),alpha=sn/(2*q),root=2*std::sqrt(a)*alpha;double b0,b1,b2,a0,a1,a2;
            if(type==0){b0=(1+cs)/2;b1=-(1+cs);b2=b0;a0=1+alpha;a1=-2*cs;a2=1-alpha;}
            else if(type==4){b0=(1-cs)/2;b1=1-cs;b2=b0;a0=1+alpha;a1=-2*cs;a2=1-alpha;}
            else if(type==1){b0=a*((a+1)-(a-1)*cs+root);b1=2*a*((a-1)-(a+1)*cs);b2=a*((a+1)-(a-1)*cs-root);a0=(a+1)+(a-1)*cs+root;a1=-2*((a-1)+(a+1)*cs);a2=(a+1)+(a-1)*cs-root;}
            else if(type==3){b0=a*((a+1)+(a-1)*cs+root);b1=-2*a*((a-1)+(a+1)*cs);b2=a*((a+1)+(a-1)*cs-root);a0=(a+1)-(a-1)*cs+root;a1=2*((a-1)-(a+1)*cs);a2=(a+1)-(a-1)*cs-root;}
            else{b0=1+alpha*a;b1=-2*cs;b2=1-alpha*a;a0=1+alpha/a;a1=-2*cs;a2=1-alpha/a;}
            target[0]=b0/a0;target[1]=b1/a0;target[2]=b2/a0;target[3]=a1/a0;target[4]=a2/a0;
        }
    } filters[6];
    double trim=1,trimTarget=1,wet=0,wetTarget=0;
    LevelMeter meter;
    void configure(const var& data,double rate){wetTarget=bool(data["enabled"])?1:0;trimTarget=std::pow(10.0,std::clamp(num(data,"trim"),-24.0,24.0)/20);const double hp=num(data,"highpass");if(hp>0)filters[0].setup(0,hp,0,.707,rate);else{filters[0].target[0]=1;for(int i=1;i<5;i++)filters[0].target[i]=0;}
        const double lp=num(data,"lowpass");if(lp>0)filters[5].setup(4,lp,0,.707,rate);else{filters[5].target[0]=1;for(int i=1;i<5;i++)filters[5].target[i]=0;}
        auto* bands=data["bands"].getArray();for(int i=0;i<4;i++){const var b=bands&&i<bands->size()?(*bands)[i]:var();const auto type=str(b,"type");filters[i+1].setup(type=="peak"?2:type=="low"?1:type=="high"?3:i==0?1:i==3?3:2,num(b,"freq",i==0?120:i==1?500:i==2?2500:8000),std::clamp(num(b,"gain"),-18.0,18.0),num(b,"q",.707),rate);}}
    void process(float& l,float& r){const float before=(l+r)*.5f;if(wetTarget==0&&wet<1e-6){wet=0;meter.add(l,r);tap(before,before);return;}wet+=(wetTarget-wet)*.002;trim+=(trimTarget-trim)*.002;const float originalL=l,originalR=r;l*=float(trim);r*=float(trim);for(auto& f:filters){f.smooth();l=f.process(l,0);r=f.process(r,1);}l=float(originalL+(l-originalL)*wet);r=float(originalR+(r-originalR)*wet);meter.add(l,r);tap(before,(l+r)*.5f);}
};
