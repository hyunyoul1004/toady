import React from 'react';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';

// 화면 레이아웃
import Header from './Header';
import Copyright from './Copyright';
import Contents from './Contents';

export function Paperbase(props) {
  const { match } = props;

  return (
    <div>
      <CssBaseline />
      <div>
        <Header />
        <Contents match={match} />
        <Box mt={8}>
          <Copyright />
        </Box>
      </div>
    </div>
  );
}

export default Paperbase;
