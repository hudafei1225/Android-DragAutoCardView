package wxj.swipeablecardstack;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class CardsDataAdapter extends ArrayAdapter<String> {
    Context context;
    public CardsDataAdapter(Context context) {
        super(context, R.layout.card_content);
        this.context = context;
    }

    @Override
    public View getView(final int position, final View contentView, ViewGroup parent){
        TextView v = (TextView)(contentView.findViewById(R.id.content));
        View ll_parent = contentView.findViewById(R.id.ll_parent);
        v.setText(getItem(position));
        ll_parent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(context, "点击了 ：" + position, Toast.LENGTH_SHORT).show();
            }
        });
        return contentView;
    }

}

